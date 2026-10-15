-- LocaSign: índices de apoio à limpeza periódica do HousekeepingJob (guia técnico, seção 12; retenção de 30 dias).
-- Sem eles, cada DELETE diário varre a tabela inteira (medido com ~500 mil linhas em outbox_events e webhook_inbox e
-- 1 milhão em processed_messages). Convenção da V1: índices nomeados com o prefixo ix_.

-- DELETE FROM outbox_events WHERE published_at IS NOT NULL AND published_at < :cutoff
-- Parcial: as linhas pendentes (published_at nulo) ficam fora do índice, que não cresce com o backlog do relay.
CREATE INDEX ix_outbox_published ON outbox_events (published_at) WHERE published_at IS NOT NULL;

-- DELETE FROM webhook_inbox WHERE received_at < :cutoff
CREATE INDEX ix_inbox_received ON webhook_inbox (received_at);

-- DELETE FROM processed_messages WHERE processed_at < :cutoff
CREATE INDEX ix_processed_at ON processed_messages (processed_at);
