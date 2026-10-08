# LocaSign — Plano de Negócio

> Nome provisório. Projeto de estudo para praticar Kotlin 2.4+, PostgreSQL, Kafka e Docker,
> integrando com a PandaDoc para gerar e enviar documentos, capturar assinaturas eletrônicas
> e reagir a webhooks. Este documento cobre só o negócio; o plano técnico será feito depois.

---

## 1. Resumo executivo

O LocaSign é um serviço backend que automatiza o ciclo de vida de contratos de locação
residencial para pequenas imobiliárias e proprietários que administram os próprios imóveis.
A partir dos dados de uma locação (imóvel, locador, locatário, valores e prazos), o sistema
gera o contrato a partir de um modelo, envia para assinatura eletrônica pela PandaDoc,
acompanha cada passo via webhooks e dispara sozinho as ações que vêm depois da assinatura:
ativar a locação, avisar os envolvidos, agendar a vistoria de entrada e registrar a primeira
cobrança.

O valor do produto não está em "assinar um PDF", que qualquer plataforma faz. Está em
transformar a assinatura em um **evento de negócio** que movimenta o resto da operação da
imobiliária, com rastreabilidade completa.

---

## 2. Problema

Em imobiliárias pequenas, o processo de contrato costuma ser manual. O corretor copia um
modelo de Word, preenche os dados à mão e envia por e-mail ou WhatsApp. Depois, precisa
cobrar as assinaturas e conferir o status manualmente. Quando o contrato finalmente é
assinado, alguém ainda precisa lembrar de agendar a vistoria e lançar a cobrança.

As consequências são concretas: erros de preenchimento (valor, CPF, datas), demora para
fechar a locação (imóvel parado é receita perdida), falta de visibilidade sobre em que pé
está cada contrato e etapas esquecidas depois da assinatura.

---

## 3. Solução e proposta de valor

| Para quem              | O que ganha                                                                                  |
|------------------------|----------------------------------------------------------------------------------------------|
| Gestor da imobiliária  | Visão em tempo real do status de todos os contratos e menos retrabalho.                      |
| Corretor               | Envia o contrato em minutos a partir dos dados já cadastrados e é avisado a cada assinatura. |
| Locatário              | Assina pelo celular, sem imprimir nada, e recebe a cópia assinada.                           |
| Proprietário (locador) | Sabe exatamente quando a locação está ativa.                                                 |

---

## 4. Público-alvo e personas

O público principal são imobiliárias com 1 a 15 corretores que administram de algumas dezenas
a poucas centenas de contratos. O público secundário são proprietários independentes com
vários imóveis. Grandes imobiliárias com ERP próprio ficam fora do foco inicial e seriam
atendidas no futuro via integração por API.

| Persona          | Contexto                                  | Dor principal                                           |
|------------------|-------------------------------------------|---------------------------------------------------------|
| Marina, gestora  | Dona de uma imobiliária com 6 corretores. | Não sabe quais contratos estão travados nem por quê.    |
| Rafael, corretor | Fecha de 8 a 12 locações por mês.         | Perde tempo preenchendo modelos e cobrando assinaturas. |
| Lucas, locatário | Quer se mudar rápido.                     | Não tem impressora e não quer ir ao escritório assinar. |

---

## 5. Jornada principal (fluxo feliz)

1. O corretor cadastra a locação com os dados do imóvel, do locatário, o valor do aluguel,
   a data de início e o prazo.
2. O sistema valida os dados e solicita a geração do contrato.
3. O contrato é criado na PandaDoc a partir do modelo, já preenchido com os dados da locação.
4. O contrato é enviado para assinatura na ordem definida: primeiro o locatário, depois o
   representante da imobiliária (que assina em nome do locador).
5. A PandaDoc avisa o sistema, via webhook, a cada mudança: documento visualizado, assinatura
   feita, recusa ou conclusão.
6. Quando todos assinam, o contrato é concluído e o PDF assinado é arquivado.
7. As ações pós-assinatura são disparadas: a locação é ativada, a vistoria de entrada é
   agendada, a primeira cobrança é registrada e todos são notificados.

### Fluxos alternativos

O locatário pode **recusar** o contrato; nesse caso o corretor é alertado com o motivo e pode
gerar uma nova versão. Se ninguém concluir dentro do prazo, o contrato **expira**. O corretor
pode **cancelar** o contrato antes da conclusão. Para **corrigir dados** depois do envio, o
contrato atual é cancelado e uma nova versão é gerada, preservando o histórico. Por fim,
webhooks podem chegar **duplicados ou fora de ordem**, e isso é tratado como comportamento
normal, não como exceção (ver regras R5 a R7).

---

## 6. Ciclo de vida do contrato

| Status                | Significado                                     | O que leva a ele           | Próximos status possíveis                                         |
|-----------------------|-------------------------------------------------|----------------------------|-------------------------------------------------------------------|
| Rascunho              | Locação cadastrada, contrato ainda não gerado.  | Cadastro da locação.       | Gerado, Cancelado                                                 |
| Gerado                | Documento criado na PandaDoc, aguardando envio. | Geração bem-sucedida.      | Enviado, Cancelado                                                |
| Enviado               | Aguardando assinaturas.                         | Envio para os signatários. | Visualizado, Parcialmente assinado, Recusado, Expirado, Cancelado |
| Visualizado           | Algum signatário abriu o documento.             | Webhook da PandaDoc.       | Parcialmente assinado, Recusado, Expirado, Cancelado              |
| Parcialmente assinado | Pelo menos um signatário assinou.               | Webhook da PandaDoc.       | Concluído, Recusado, Expirado, Cancelado                          |
| Concluído             | Todos assinaram.                                | Webhook da PandaDoc.       | Final                                                             |
| Recusado              | Um signatário recusou.                          | Webhook da PandaDoc.       | Final (permite nova versão)                                       |
| Expirado              | Prazo encerrado sem conclusão.                  | Regra interna de prazo.    | Final (permite nova versão)                                       |
| Cancelado             | O corretor desistiu ou precisou corrigir dados. | Ação do usuário.           | Final (permite nova versão)                                       |

O status nunca regride e estados finais são imutáveis. Um webhook que tentaria levar o
contrato "para trás" é registrado na auditoria, mas não altera o status.

---

## 7. Eventos de negócio

Cada fato importante vira um evento publicado para que outras partes do sistema reajam de
forma independente. É aqui que o Kafka entra: quem gera o evento não precisa saber quem vai
reagir a ele, e novas reações podem ser adicionadas sem mexer no fluxo principal.

| Evento                 | Quando ocorre                   | Quem reage                                                 | Efeito esperado                                                                      |
|------------------------|---------------------------------|------------------------------------------------------------|--------------------------------------------------------------------------------------|
| Locação cadastrada     | Corretor finaliza o cadastro.   | Gerador de contratos.                                      | Cria o documento na PandaDoc.                                                        |
| Contrato gerado        | PandaDoc confirma a criação.    | Envio.                                                     | Envia para os signatários.                                                           |
| Contrato enviado       | Envio confirmado.               | Notificações.                                              | Avisa o corretor.                                                                    |
| Contrato visualizado   | Signatário abre o documento.    | Status e métricas.                                         | Atualiza status e registra o horário.                                                |
| Signatário assinou     | Uma assinatura é feita.         | Status e notificações.                                     | Atualiza status e avisa o corretor que falta uma assinatura.                         |
| Contrato concluído     | Todos assinaram.                | Arquivamento, locação, vistoria, financeiro, notificações. | Arquiva o PDF, ativa a locação, agenda a vistoria, cria a 1ª cobrança e avisa todos. |
| Contrato recusado      | Signatário recusa.              | Status e notificações.                                     | Alerta o corretor com o motivo.                                                      |
| Contrato expirado      | Prazo vence.                    | Status e notificações.                                     | Alerta o corretor.                                                                   |
| Falha de processamento | Uma reação falha repetidamente. | Fila de erros e auditoria.                                 | Permite investigar e reprocessar.                                                    |

No MVP, vistoria, financeiro e notificações são **simulados**: o sistema apenas registra que
a ação aconteceria. Não há integração real com e-mail, WhatsApp ou meio de pagamento.

---

## 8. Regras de negócio

- **R1.** Uma locação só pode ter um contrato não final por vez.
- **R2.** Dados obrigatórios: nome e CPF válido do locatário, endereço do imóvel, valor do
  aluguel maior que zero, data de início igual ou posterior à data do cadastro e prazo em
  meses (padrão de 30 meses, comum no mercado de locação residencial).
- **R3.** Ordem de assinatura: primeiro o locatário, depois o representante da imobiliária.
- **R4.** Prazo para assinatura de 7 dias, com lembrete no 3º dia (o lembrete é opcional no MVP).
- **R5.** O status nunca regride e estados finais são imutáveis.
- **R6.** O mesmo evento recebido mais de uma vez nunca produz efeito duplicado (por exemplo,
  nunca duas cobranças para o mesmo contrato).
- **R7.** Todo evento recebido da PandaDoc é registrado como chegou, antes de qualquer
  processamento, para auditoria e reprocessamento.
- **R8.** Ações pós-assinatura só acontecem quando o contrato está Concluído.
- **R9.** Correções após o envio exigem cancelar e gerar uma nova versão; todas as versões
  ficam no histórico da locação.
- **R10.** O projeto usa apenas dados fictícios e guarda o mínimo de dados pessoais.

---

## 9. Escopo do MVP

### Entra

- Cadastro de locação via API.
- Um modelo de contrato de locação residencial criado na PandaDoc.
- Geração e envio do contrato para 2 signatários.
- Recebimento de webhooks, com registro bruto e trilha de auditoria.
- Atualização de status seguindo o ciclo de vida da seção 6.
- Eventos de negócio no Kafka com pelo menos três consumidores: status, notificações (simuladas) e pós-assinatura
  (simulado).
- Consulta do status atual e do histórico completo de um contrato.
- Ambiente local completo (aplicação, PostgreSQL e Kafka) subindo com Docker.

### Fica de fora

- Frontend ou painel visual (o uso é via API e uma ferramenta de requisições).
- Login, usuários e múltiplas imobiliárias (multi-tenant).
- Fiador e testemunhas, por causa do limite de 2 signatários do plano gratuito.
- Integrações reais com e-mail, WhatsApp ou pagamentos.
- Assinatura embutida (iframe) no próprio produto.
- Planos, cobrança dos clientes e deploy em nuvem.

---

## 10. Restrições da PandaDoc e adaptações

| Restrição                                                        | Impacto                                 | Adaptação                                                                          |
|------------------------------------------------------------------|-----------------------------------------|------------------------------------------------------------------------------------|
| Plano gratuito permite 2 destinatários por documento.            | Sem fiador ou testemunhas.              | Fluxo com locatário e imobiliária; fiador fica como evolução.                      |
| Plano gratuito permite 5 modelos.                                | Poucos tipos de documento.              | 1 modelo no MVP; no futuro, até 3 (locação, aditivo, distrato).                    |
| Plano gratuito permite 60 documentos enviados por ano.           | Cota pequena.                           | Desenvolver com a chave sandbox; usar produção só no teste final.                  |
| Sandbox só envia para e-mails do mesmo domínio do remetente.     | Não dá para enviar a e-mails externos.  | Usar endereços e aliases do próprio domínio para simular locatário e imobiliária.  |
| Sandbox tem limite de 10 requisições por minuto por endpoint.    | Testes em volume travam.                | Testar com poucos contratos e tratar a resposta de limite excedido.                |
| Sandbox gera PDFs com marca d'água e prefixo "[DEV]".            | Documentos não servem para uso real.    | Aceitável para estudo.                                                             |
| Download do PDF assinado exige chave de produção.                | Arquivamento não funciona no sandbox.   | No sandbox, guardar só a referência; testar o download uma vez com produção.       |
| Webhooks precisam de URL pública com HTTPS.                      | Ambiente local não é acessível de fora. | Usar um túnel (ngrok ou Cloudflare Tunnel) e também webhooks simulados nos testes. |
| Disponibilidade de webhooks varia conforme plano e configuração. | Risco de bloqueio do fluxo principal.   | Verificar logo no início; plano B é consultar o status periodicamente (polling).   |

---

## 11. Modelo de receita (hipotético)

Como exercício de negócio, o LocaSign seria vendido para imobiliárias em dois formatos.

| Plano        | Preço hipotético                        | Para quem                                    |
|--------------|-----------------------------------------|----------------------------------------------|
| Essencial    | R$ 99/mês, até 20 contratos concluídos  | Imobiliárias muito pequenas e proprietários. |
| Profissional | R$ 249/mês, até 80 contratos concluídos | Imobiliárias em crescimento.                 |
| Por uso      | R$ 9 por contrato concluído             | Volume irregular.                            |

Os custos principais seriam o custo por documento da API da PandaDoc no plano pago contratado,
a infraestrutura (banco, mensageria e hospedagem) e o suporte. A margem por contrato depende
diretamente do custo por documento da PandaDoc, que precisa ser consultado na tabela de preços
da API.

Uma observação honesta: para um produto real desse porte, Kafka gerenciado provavelmente seria
caro e desnecessário no início. Aqui ele está presente por ser objetivo de aprendizado, e o
desenho orientado a eventos continua válido mesmo com uma mensageria mais simples.

---

## 12. Concorrência e diferencial

Plataformas genéricas de assinatura (como Clicksign, ZapSign, D4Sign, DocuSign e a própria
PandaDoc) são fortes em assinar, mas não conhecem a operação de locação. ERPs imobiliários
são completos, porém costumam ser caros e complexos para imobiliárias pequenas. O diferencial
do LocaSign é ficar no meio: foco total no fluxo de locação, pós-assinatura automatizado e
rastreabilidade de cada evento.

---

## 13. Métricas de sucesso

As métricas de produto são o tempo médio entre envio e conclusão, a taxa de conclusão, a taxa
de recusa e de expiração e o número de etapas manuais eliminadas por contrato.

As métricas de confiabilidade são o percentual de webhooks processados com sucesso na primeira
tentativa, a quantidade de eventos duplicados corretamente ignorados, o tempo entre o webhook
chegar e o status ser atualizado e a quantidade de eventos parados na fila de erros.

---

## 14. Riscos e mitigações

| Risco                                                            | Probabilidade   | Impacto          | Mitigação                                                                                                                                                                                 |
|------------------------------------------------------------------|-----------------|------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Webhooks indisponíveis na conta.                                 | Média           | Alto             | Verificar logo no início; polling como plano B; webhooks simulados.                                                                                                                       |
| Escopo grande para o prazo.                                      | Alta            | Médio            | Cortes definidos na seção 16.                                                                                                                                                             |
| Projeto pronto, mas não compreendido, porque a IA escreveu tudo. | Alta            | Alto             | Regras de aprendizado da seção 15.                                                                                                                                                        |
| Eventos duplicados ou fora de ordem.                             | Alta            | Médio            | Regras R5, R6 e R7.                                                                                                                                                                       |
| Configuração de Kafka e Docker consumir o dia.                   | Média           | Médio            | Subir a infraestrutura primeiro, logo cedo.                                                                                                                                               |
| Validade jurídica e LGPD em uso real.                            | Baixa no estudo | Alto em produção | Dados fictícios; antes de qualquer uso real, validar com um advogado (no Brasil, a validade de assinaturas eletrônicas em contratos privados se apoia principalmente na MP 2.200-2/2001). |

---

## 15. Objetivos de aprendizado

| Capacidade de negócio                   | O que pratica                                                                     |
|-----------------------------------------|-----------------------------------------------------------------------------------|
| Modelar locação, partes e contrato.     | Kotlin: data classes, null safety, value classes e validação de domínio.          |
| Ciclo de vida com estados e transições. | Kotlin: hierarquias seladas e `when` exaustivo.                                   |
| Chamadas à PandaDoc.                    | Cliente HTTP, coroutines, tratamento de erros e de limite de requisições.         |
| Receber webhooks.                       | Endpoints, validação de que a chamada veio mesmo da PandaDoc e respostas rápidas. |
| Persistir e auditar.                    | PostgreSQL, migrations e transações.                                              |
| Publicar e consumir eventos.            | Kafka: produtores, consumidores, grupos, reprocessamento e fila de erros.         |
| Não duplicar efeitos.                   | Idempotência e o padrão outbox.                                                   |
| Ambiente reproduzível.                  | Docker Compose.                                                                   |

### Regras para aprender usando agentes de IA

1. Peça para o agente de IA explicar cada decisão antes de aceitar o código.
2. Escreva você mesmo as regras do ciclo de vida (seções 6 e 8) e deixe a IA cuidar do
   boilerplate e da configuração.
3. Faça commits pequenos, um por capacidade de negócio.
4. Ao fim de cada dia, explique o fluxo completo no README sem olhar o código.

---

## 16. Cronograma

| Dia                  | Entrega                                                                                                                                                                                                                          |
|----------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Quinta, 08/10 (hoje) | Conta e chave sandbox da PandaDoc criadas, webhooks verificados, modelo de contrato com campos criado na PandaDoc. Infraestrutura no ar. Cadastro → geração → envio funcionando. Webhook recebido e gravado. Status consultável. |
| Sexta, 09/10         | Webhooks viram eventos no Kafka com os três consumidores. Ciclo de vida completo com regras R5 a R7. Recusa e cancelamento funcionando.                                                                                          |
| Sábado, 10/10        | Expiração e lembrete, fila de erros e reprocessamento, testes, README e demonstração ponta a ponta. Teste final com chave de produção (1 ou 2 documentos).                                                                       |

**Se for só hoje**, o corte mínimo é: cadastro → geração → envio → webhook gravado → um evento
no Kafka → status atualizado por um único consumidor.

---

## 17. Critérios de aceite

1. Dada uma locação válida, quando o contrato é solicitado, um documento é criado na PandaDoc
   e enviado aos 2 signatários na ordem definida.
2. Quando o locatário assina, o status passa a Parcialmente assinado e o corretor é notificado (simulado).
3. Quando todos assinam, o status passa a Concluído e as ações pós-assinatura acontecem
   exatamente uma vez.
4. Se o mesmo webhook chega duas vezes, nenhum efeito é duplicado.
5. Se um webhook antigo chega depois da conclusão, o status não muda, mas o evento fica
   registrado.
6. Se o locatário recusa, o status passa a Recusado e o corretor é alertado.
7. O histórico completo de um contrato pode ser consultado.
8. Todo o ambiente sobe com um único comando.

---

## 18. Glossário

| Termo               | Significado                                                                               |
|---------------------|-------------------------------------------------------------------------------------------|
| Locador             | Proprietário do imóvel.                                                                   |
| Locatário           | Inquilino.                                                                                |
| Fiador              | Garantidor do contrato (fora do MVP).                                                     |
| Vistoria de entrada | Registro do estado do imóvel antes da mudança.                                            |
| Signatário          | Pessoa que precisa assinar o documento.                                                   |
| Modelo (template)   | Documento base na PandaDoc com campos a preencher.                                        |
| Webhook             | Chamada que a PandaDoc faz ao sistema quando algo muda no documento.                      |
| Evento de negócio   | Fato relevante publicado para que outras partes reajam.                                   |
| Idempotência        | Garantia de que processar o mesmo evento duas vezes tem o mesmo efeito que processar uma. |

---

## 19. Próximo passo

Salvar este arquivo no repositório (por exemplo, `docs/plano-de-negocio.md`) e usá-lo como
base para o plano técnico com agentes de IA: arquitetura, módulos, modelo de dados, tópicos,
endpoints e estratégia de testes.