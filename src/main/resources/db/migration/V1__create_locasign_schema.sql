-- LocaSign: schema inicial do MVP (guia técnico, seção 8).
-- Convenções: snake_case, instantes em timestamptz (UTC), dinheiro em numeric(12,2),
-- status como text com CHECK, chaves primárias uuid geradas no domínio.

CREATE TABLE leases (
    id                  uuid          PRIMARY KEY,
    tenant_name         text          NOT NULL,
    tenant_cpf          text          NOT NULL,
    tenant_email        text          NOT NULL,
    agency_signer_name  text          NOT NULL,
    agency_signer_email text          NOT NULL,
    property_address    text          NOT NULL,
    rent_amount         numeric(12,2) NOT NULL,
    start_date          date          NOT NULL,
    term_months         integer       NOT NULL,
    status              text          NOT NULL DEFAULT 'REGISTERED',
    activated_at        timestamptz,
    row_version         bigint        NOT NULL,
    created_at          timestamptz   NOT NULL,
    CONSTRAINT ck_leases_rent_amount CHECK (rent_amount > 0),
    CONSTRAINT ck_leases_term_months CHECK (term_months BETWEEN 1 AND 120),
    CONSTRAINT ck_leases_status CHECK (status IN ('REGISTERED', 'ACTIVE'))
);

CREATE TABLE contracts (
    id                        uuid        PRIMARY KEY,
    lease_id                  uuid        NOT NULL REFERENCES leases (id),
    version_number            integer     NOT NULL,
    status                    text        NOT NULL,
    provider_document_id      text,
    provider_last_modified_at timestamptz,
    sent_at                   timestamptz,
    expires_at                timestamptz,
    reminder_sent_at          timestamptz,
    last_reconciled_at        timestamptz,
    cancel_reason             text,
    signed_document_ref       text,
    row_version               bigint      NOT NULL,
    created_at                timestamptz NOT NULL,
    updated_at                timestamptz NOT NULL,
    CONSTRAINT ck_contracts_version CHECK (version_number >= 1),
    CONSTRAINT ck_contracts_status CHECK (status IN (
        'DRAFT', 'GENERATED', 'SENT', 'VIEWED', 'PARTIALLY_SIGNED',
        'COMPLETED', 'DECLINED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT uq_contracts_lease_version UNIQUE (lease_id, version_number),
    CONSTRAINT uq_contracts_provider_document UNIQUE (provider_document_id)
);

-- R1: uma locação só pode ter um contrato não final por vez.
CREATE UNIQUE INDEX ux_contracts_one_active_per_lease
    ON contracts (lease_id)
    WHERE status NOT IN ('COMPLETED', 'DECLINED', 'EXPIRED', 'CANCELLED');

-- Apoio aos jobs de expiração, lembrete e reconciliação (somente contratos não finais).
CREATE INDEX ix_contracts_open_by_expiry
    ON contracts (expires_at)
    WHERE status IN ('SENT', 'VIEWED', 'PARTIALLY_SIGNED');

CREATE INDEX ix_contracts_open_by_update
    ON contracts (updated_at)
    WHERE status NOT IN ('COMPLETED', 'DECLINED', 'EXPIRED', 'CANCELLED');

CREATE TABLE contract_signers (
    id            uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_id   uuid        NOT NULL REFERENCES contracts (id) ON DELETE CASCADE,
    role          text        NOT NULL,
    name          text        NOT NULL,
    email         text        NOT NULL,
    signing_order integer     NOT NULL,
    completed_at  timestamptz,
    CONSTRAINT ck_contract_signers_role CHECK (role IN ('TENANT', 'AGENCY')),
    CONSTRAINT ck_contract_signers_order CHECK (signing_order >= 1),
    CONSTRAINT uq_contract_signers_role UNIQUE (contract_id, role)
);

-- Trilha de auditoria. `INFO` registra fatos que não mudam o status (assinatura de um signatário, arquivamento...).
CREATE TABLE contract_status_history (
    seq             bigint      GENERATED ALWAYS AS IDENTITY,
    id              uuid        PRIMARY KEY,
    contract_id     uuid        NOT NULL REFERENCES contracts (id) ON DELETE CASCADE,
    from_status     text,
    to_status       text        NOT NULL,
    outcome         text        NOT NULL,
    source          text        NOT NULL,
    source_event_id text,
    note            text,
    occurred_at     timestamptz NOT NULL,
    CONSTRAINT ck_history_outcome CHECK (outcome IN ('APPLIED', 'IGNORED_TRANSITION', 'INFO')),
    CONSTRAINT ck_history_source CHECK (source IN ('API', 'WEBHOOK', 'RECONCILIATION', 'SCHEDULER'))
);

CREATE INDEX ix_history_contract ON contract_status_history (contract_id, seq);

-- R7: entregas de webhook registradas exatamente como chegaram (texto preserva os bytes).
CREATE TABLE webhook_inbox (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    delivery_id text        NOT NULL,
    raw_body    text        NOT NULL,
    received_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_webhook_inbox_delivery UNIQUE (delivery_id)
);

CREATE TABLE outbox_events (
    seq            bigint      GENERATED ALWAYS AS IDENTITY,
    id             text        PRIMARY KEY,
    aggregate_type text        NOT NULL,
    aggregate_id   text        NOT NULL,
    event_type     text        NOT NULL,
    topic          text        NOT NULL,
    message_key    text        NOT NULL,
    payload        jsonb       NOT NULL,
    headers        jsonb       NOT NULL DEFAULT '{}'::jsonb,
    created_at     timestamptz NOT NULL DEFAULT now(),
    published_at   timestamptz,
    attempts       integer     NOT NULL DEFAULT 0,
    last_error     text,
    available_at   timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_outbox_pending ON outbox_events (seq) WHERE published_at IS NULL;

-- R6: idempotência dos consumidores.
CREATE TABLE processed_messages (
    consumer_group text        NOT NULL,
    event_id       text        NOT NULL,
    processed_at   timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (consumer_group, event_id)
);

-- R6/R8: efeitos pós-assinatura executados no máximo uma vez por contrato.
CREATE TABLE post_signature_actions (
    id          uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_id uuid        NOT NULL REFERENCES contracts (id),
    action_type text        NOT NULL,
    status      text        NOT NULL,
    executed_at timestamptz NOT NULL DEFAULT now(),
    details     jsonb,
    CONSTRAINT uq_post_signature_action UNIQUE (contract_id, action_type)
);

CREATE TABLE notifications_log (
    id               uuid        PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_id      uuid        NOT NULL,
    event_id         text        NOT NULL,
    event_type       text        NOT NULL,
    recipient_masked text        NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_notifications_contract ON notifications_log (contract_id, created_at);
