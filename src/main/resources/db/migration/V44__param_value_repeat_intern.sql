-- Two changes to how a check-in's param value is stored, both moving the decision out of guesswork
-- and into the data:
--
-- 1. Numbers get their own typed column. Until now every value — text or number — lived in the
--    TEXT `value` column, so a logged quantity was stored as the string "5.0". Give numeric params a
--    real `value_num` and stop stuffing numbers through text.
--
-- 2. Text interning becomes automatic and value-driven. V43 gated the `param_values` dictionary on a
--    per-param `low_cardinality` flag set up front. Replace it with a decision made per value: a text
--    value stays inline the first time it is seen and is folded into the dictionary the moment it
--    repeats (its earlier inline row migrated along with it). Singletons cost a plain TEXT; anything
--    that recurs is deduped. Numbers are never interned — they aren't a free-text vocabulary.
--
-- The `low_cardinality` flag is now dead and dropped.

ALTER TABLE checkin_values
    ADD COLUMN value_num DOUBLE PRECISION;

-- Backfill: pull every numeric param's value out of the text storage into the typed column.
-- Inline numbers first, then any that a V43 low-cardinality number param had interned (an edge case,
-- since the flag was new and opt-in), and finally drop those now-orphan numeric dictionary rows.
UPDATE checkin_values v
SET value_num = v.value::double precision, value = NULL
FROM habit_params p
WHERE p.id = v.param_id AND p.param_type = 'number' AND v.value IS NOT NULL;

UPDATE checkin_values v
SET value_num = pv.value::double precision, value_id = NULL
FROM habit_params p, param_values pv
WHERE p.id = v.param_id AND p.param_type = 'number' AND v.value_id = pv.id;

DELETE FROM param_values pv
USING habit_params p
WHERE pv.param_id = p.id AND p.param_type = 'number';

-- Write side, rewritten. Returns the (value, value_id, value_num) triple to drop into
-- `checkin_values`:
--   * NULL text or unknown param      -> store nothing (all NULL),
--   * numeric param                   -> the typed number, return (NULL, NULL, num),
--   * text already in the dictionary  -> reuse it, return (NULL, id, NULL),
--   * text already present inline      -> 2nd sighting: intern it, migrate the inline row(s),
--                                         return (NULL, id, NULL),
--   * text never seen before          -> 1st sighting: keep it inline, return (text, NULL, NULL).
CREATE OR REPLACE FUNCTION store_param_value(p_param_id BIGINT, p_value TEXT,
                                  OUT value TEXT, OUT value_id BIGINT, OUT value_num DOUBLE PRECISION)
    LANGUAGE plpgsql AS $$
DECLARE
    v_id BIGINT;
BEGIN
    value := NULL;
    value_id := NULL;
    value_num := NULL;
    IF p_value IS NULL OR p_param_id IS NULL THEN
        RETURN;
    END IF;

    -- Numbers go to the typed column and are never interned.
    IF (SELECT param_type FROM habit_params WHERE id = p_param_id) <> 'text' THEN
        value_num := p_value::double precision;
        RETURN;
    END IF;

    -- Already interned (i.e. it has repeated before): keep pointing at the dictionary row.
    SELECT id INTO v_id FROM param_values
    WHERE param_id = p_param_id AND value = p_value;
    IF FOUND THEN
        value_id := v_id;
        RETURN;
    END IF;

    -- An inline copy already exists, so this write is the value's second sighting: it just became
    -- "repeated". Intern it and repoint every earlier inline row onto the new dictionary entry.
    IF EXISTS (SELECT 1 FROM checkin_values
               WHERE param_id = p_param_id AND value = p_value) THEN
        v_id := intern_param_value(p_param_id, p_value);
        UPDATE checkin_values
        SET value = NULL, value_id = v_id
        WHERE param_id = p_param_id AND value = p_value;
        value_id := v_id;
        RETURN;
    END IF;

    -- First sighting: store it inline, dictionary untouched.
    value := p_value;
END;
$$;

-- Read side: resolve a `checkin_values` row's stored form back to text — the inline value if present,
-- otherwise the dictionary entry behind `value_id`, otherwise the numeric column. Numbers render
-- without a forced decimal (5, not 5.0); callers parse the text back to a double.
CREATE OR REPLACE FUNCTION read_param_value(p_value TEXT, p_value_id BIGINT, p_value_num DOUBLE PRECISION)
    RETURNS TEXT
    LANGUAGE sql STABLE AS $$
    SELECT COALESCE(
        p_value,
        (SELECT value FROM param_values WHERE id = p_value_id),
        p_value_num::text
    );
$$;

-- Replaced by the 3-arg form above.
DROP FUNCTION read_param_value(TEXT, BIGINT);

ALTER TABLE habit_params DROP COLUMN low_cardinality;
