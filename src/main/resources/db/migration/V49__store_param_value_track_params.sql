-- Follow-up to V47: store_param_value still read param_type from the old habit_params table,
-- which V47 renamed to track_params. Repoint the function body. Body otherwise unchanged.
CREATE OR REPLACE FUNCTION store_param_value(
    p_param_id BIGINT,
    p_value TEXT,
    OUT value TEXT,
    OUT value_id BIGINT,
    OUT value_num DOUBLE PRECISION
) RETURNS RECORD
LANGUAGE plpgsql
AS $$
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
    IF (SELECT param_type FROM track_params WHERE id = p_param_id) <> 'text' THEN
        value_num := p_value::double precision;
        RETURN;
    END IF;

    -- Already interned (i.e. it has repeated before): keep pointing at the dictionary row.
    SELECT id INTO v_id FROM param_values
    WHERE param_id = p_param_id AND param_values.value = p_value;
    IF FOUND THEN
        value_id := v_id;
        RETURN;
    END IF;

    -- An inline copy already exists, so this write is the value's second sighting: it just became
    -- "repeated". Intern it and repoint every earlier inline row onto the new dictionary entry.
    IF EXISTS (SELECT 1 FROM checkin_values
               WHERE param_id = p_param_id AND checkin_values.value = p_value) THEN
        v_id := intern_param_value(p_param_id, p_value);
        UPDATE checkin_values
        SET value = NULL, value_id = v_id
        WHERE param_id = p_param_id AND checkin_values.value = p_value;
        value_id := v_id;
        RETURN;
    END IF;

    -- First sighting: store it inline, dictionary untouched.
    value := p_value;
END;
$$;
