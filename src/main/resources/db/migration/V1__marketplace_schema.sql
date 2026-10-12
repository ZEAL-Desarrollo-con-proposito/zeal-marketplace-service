-- Esquema del Business Domain: convocatorias internas y talleres con cupos limitados.

CREATE TABLE projects (
    id           VARCHAR(64)  PRIMARY KEY,
    organization VARCHAR(120) NOT NULL,
    title        VARCHAR(80)  NOT NULL,
    area         VARCHAR(40)  NOT NULL,
    cohort       VARCHAR(40)  NOT NULL,
    summary      VARCHAR(140) NOT NULL,
    description  JSONB        NOT NULL,
    requirements JSONB        NOT NULL,
    skills       JSONB        NOT NULL,
    capacity     INTEGER      NOT NULL CHECK (capacity > 0),
    occupied     INTEGER      NOT NULL DEFAULT 0,
    closes_at    TIMESTAMPTZ  NOT NULL,
    published_at TIMESTAMPTZ  NOT NULL,
    version      BIGINT       NOT NULL DEFAULT 0,
    -- Reto C-1: aunque fallara la lógica, la base de datos jamás acepta sobreasignación.
    CONSTRAINT ck_projects_no_overbooking CHECK (occupied >= 0 AND occupied <= capacity)
);
CREATE INDEX ix_projects_organization_closes ON projects (organization, closes_at);

CREATE TABLE applications (
    id                VARCHAR(64) PRIMARY KEY,
    project_id        VARCHAR(64) NOT NULL REFERENCES projects (id),
    user_id           VARCHAR(64) NOT NULL,
    status            VARCHAR(20) NOT NULL CHECK (status IN ('ACEPTADA', 'EN_ESPERA')),
    waitlist_position INTEGER,
    created_at        TIMESTAMPTZ NOT NULL,
    version           BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uq_applications_project_user UNIQUE (project_id, user_id),
    CONSTRAINT ck_applications_waitlist CHECK (
        (status = 'ACEPTADA' AND waitlist_position IS NULL) OR (status = 'EN_ESPERA' AND waitlist_position > 0))
);
CREATE INDEX ix_applications_user ON applications (user_id, created_at);

CREATE TABLE workshops (
    id               VARCHAR(64)  PRIMARY KEY,
    organization     VARCHAR(120) NOT NULL,
    title            VARCHAR(80)  NOT NULL,
    description      VARCHAR(600) NOT NULL,
    starts_at        TIMESTAMPTZ  NOT NULL,
    duration_minutes INTEGER      NOT NULL CHECK (duration_minutes > 0),
    capacity         INTEGER      NOT NULL CHECK (capacity > 0),
    occupied         INTEGER      NOT NULL DEFAULT 0,
    published_at     TIMESTAMPTZ  NOT NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT ck_workshops_no_overbooking CHECK (occupied >= 0 AND occupied <= capacity)
);
CREATE INDEX ix_workshops_organization_starts ON workshops (organization, starts_at);

CREATE TABLE enrollments (
    id          VARCHAR(64) PRIMARY KEY,
    workshop_id VARCHAR(64) NOT NULL REFERENCES workshops (id),
    user_id     VARCHAR(64) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    version     BIGINT      NOT NULL DEFAULT 0,
    CONSTRAINT uq_enrollments_workshop_user UNIQUE (workshop_id, user_id)
);
CREATE INDEX ix_enrollments_user ON enrollments (user_id);
