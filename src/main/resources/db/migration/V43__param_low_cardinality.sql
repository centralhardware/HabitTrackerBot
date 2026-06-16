-- Low-cardinality params: dedupe their endlessly repeated text values into a per-param
-- dictionary, the relational equivalent of ClickHouse's LowCardinality(String).
--
-- A param opts in via `habit_params.low_cardinality`. All the branching lives in two functions:
-- on write `store_param_value(param_id, text)` decides whether to intern the text into the
-- `param_values` dictionary (returning a `value_id`) or keep it inline; on read
-- `read_param_value(value, value_id)` resolves whichever was stored back to text. The repository
-- queries just call these — no CASE logic, no joins to the dictionary.
--
-- The flag defaults FALSE, so all existing rows stay as inline text — no backfill, opt-in per param.

ALTER TABLE habit_params
    ADD COLUMN low_cardinality BOOLEAN NOT NULL DEFAULT FALSE;

-- The dictionary: one row per distinct value a low-cardinality param has ever seen.
CREATE TABLE param_values (
    id       BIGSERIAL PRIMARY KEY,
    param_id BIGINT NOT NULL REFERENCES habit_params (id) ON DELETE CASCADE,
    value    TEXT   NOT NULL,
    UNIQUE (param_id, value)
);

-- RESTRICT: a dictionary entry can't be dropped while check-ins still point at it.
ALTER TABLE checkin_values
    ADD COLUMN value_id BIGINT REFERENCES param_values (id) ON DELETE RESTRICT;

-- Interning: return the dictionary id for (param, text), inserting it on first sight. The loop
-- handles the race where two concurrent check-ins intern the same new value: the loser's INSERT
-- hits ON CONFLICT DO NOTHING (NULL id), so it loops back and SELECTs the winner's id.
CREATE FUNCTION intern_param_value(p_param_id BIGINT, p_value TEXT)
    RETURNS BIGINT
    LANGUAGE plpgsql AS $$
DECLARE
    v_id BIGINT;
BEGIN
    LOOP
        SELECT id INTO v_id FROM param_values
        WHERE param_id = p_param_id AND value = p_value;
        IF FOUND THEN
            RETURN v_id;
        END IF;

        INSERT INTO param_values (param_id, value)
        VALUES (p_param_id, p_value)
        ON CONFLICT (param_id, value) DO NOTHING
        RETURNING id INTO v_id;
        IF v_id IS NOT NULL THEN
            RETURN v_id;
        END IF;
        -- Lost the race; loop to read the winner's id.
    END LOOP;
END;
$$;

-- Write side: given a param and the text a check-in carries, decide how to store it and hand back
-- the (value, value_id) pair to drop straight into `checkin_values`. The INSERTs call this once
-- per value instead of branching themselves:
--   * NULL text or unknown param -> store nothing (both NULL),
--   * low-cardinality param      -> intern into the dictionary, return (NULL, id),
--   * any other param            -> keep it inline, return (text, NULL).
CREATE FUNCTION store_param_value(p_param_id BIGINT, p_value TEXT,
                                  OUT value TEXT, OUT value_id BIGINT)
    LANGUAGE plpgsql AS $$
BEGIN
    value := NULL;
    value_id := NULL;
    IF p_value IS NULL OR p_param_id IS NULL THEN
        RETURN;
    END IF;
    IF (SELECT low_cardinality FROM habit_params WHERE id = p_param_id) THEN
        value_id := intern_param_value(p_param_id, p_value);
    ELSE
        value := p_value;
    END IF;
END;
$$;

-- Read side: resolve a `checkin_values` row's stored form back to its text — the inline value if
-- present, otherwise the dictionary entry behind `value_id`. Replaces COALESCE + a dictionary join.
CREATE FUNCTION read_param_value(p_value TEXT, p_value_id BIGINT)
    RETURNS TEXT
    LANGUAGE sql STABLE AS $$
    SELECT COALESCE(p_value, (SELECT value FROM param_values WHERE id = p_value_id));
$$;
