CREATE EXTENSION IF NOT EXISTS plpgsql;

DO $$
DECLARE
  r_name  text;
  r_pass  text;
  sch_owner text;
  dbname text := current_database();
BEGIN
  FOR r_name, r_pass IN
    SELECT *
    FROM (VALUES
      ('camunda7', 'camunda7_pwd')
    ) AS roles(role_name, role_pass)
  LOOP
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = r_name) THEN
      EXECUTE format('CREATE ROLE %I LOGIN PASSWORD %L', r_name, r_pass);
    ELSE
      EXECUTE format('ALTER ROLE %I WITH LOGIN PASSWORD %L', r_name, r_pass);
    END IF;

    EXECUTE format('CREATE SCHEMA IF NOT EXISTS %I', r_name);
    SELECT pg_get_userbyid(nspowner) INTO sch_owner
      FROM pg_namespace WHERE nspname = r_name;
    IF sch_owner IS DISTINCT FROM r_name THEN
      EXECUTE format('ALTER SCHEMA %I OWNER TO %I', r_name, r_name);
    END IF;

    EXECUTE format(
      'ALTER ROLE %I IN DATABASE %I SET search_path = %I, public',
      r_name, dbname, r_name
    );
  END LOOP;
END$$;
