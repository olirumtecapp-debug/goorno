# CHECKPOINT GOORNO — Checkpoint #014 (Build 72)
**Data:** 24 de Outubro de 2026  
**Status do Projeto:** ✅ Auditoria Geral Concluída (Código Android, Backend, Segurança e Documentos Jurídicos Adequados)

---

## 1. O que foi consolidado no Checkpoint #014:

### A. Correções Críticas no Código Android
1. **Blindagem de Consentimento e Proteção do Histórico (`DriverPreferencesRepository.kt`):**
   - Corrigido o disparo de sincronização na nuvem: agora estritamente condicionado ao consentimento explícito do usuário (`config.cloudBackupConsent`).
   - Eliminado o bug crítico onde salvar configurações locais enviava `offers = emptyList()`. Agora o sistema restaura e preserva as ofertas pré-existentes na nuvem.
2. **Integridade de Campos de Duração e Consentimento (`CloudSyncManager.kt`):**
   - Adicionada persistência dos campos `pickupDurationMin` e `tripDurationMin` tanto no `exportToJsonString` quanto no `importFromJsonString` (anteriormente anulados na importação).
   - O padrão de importação de `cloudBackupConsent` agora é `false` (opt-in deliberado, em conformidade com a LGPD).
   - Removida a coação de `cloudBackupConsent = true` no método `uploadBackupByDriverCode`.
3. **Eliminação do Anti-padrão de Looper Infinito (`GoOrNoApplication.kt`):**
   - Removido o loop infinito `while (true) { Looper.loop() }` ("Cockroach Pattern"), que mascarava falhas críticas e corrompia o ciclo de vida do Android. Substituído por tratamento e monitoramento de falhas limpo e estruturado.

### B. Segurança e Backend (`server`)
1. **Hashing Criptográfico de PIN e Anti-Brute Force (`licenseStore.ts`):**
   - Implementado hash SHA-256 com salt para todos os novos PINs ou redefinições, mantendo compatibilidade transparente com PINs existentes e migrando-os automaticamente para hash no primeiro uso.
   - Implementada proteção contra força bruta de PIN de 4 dígitos: após 5 tentativas incorretas, a conta é temporariamente bloqueada por 15 minutos.
2. **Segurança de Credenciais Administrativas (`server/index.ts`):**
   - Senha mestre parametrizada por `process.env.ADMIN_PASSWORD` mantendo fallback seguro para a chave oficial.

### C. Harmonização e Auditoria dos Documentos Jurídicos (`legalTexts.ts`)
1. **Identificação do Fornecedor e Canais Oficiais:**
   - Inseridos placeholders formais e instruções claras para inserção de Razão Social, CNPJ/CPF e Endereço Comercial, sem invenção de dados fictícios.
2. **Direito Legal de Arrependimento (CDC Art. 49):**
   - Removida redação conflitante ("pagamentos irrevogáveis"). Inserida cláusula cristalina garantindo o direito de arrependimento no prazo de 7 (sete) dias corridos para qualquer contratação ou renovação via PIX, com estorno integral via suporte ou Asaas.
   - Esclarecido o modelo de contratação por períodos pré-pagos (30, 90, 180 ou 365 dias) sem renovação automática ou cobrança surpresa no cartão.
3. **Privacidade e LGPD (Lei nº 13.709/2018):**
   - Atualizado o inventário técnico real: processamento 100% local na memória RAM por padrão; envio à nuvem estritamente opcional mediante consentimento.
   - Especificados os canais de suporte para exclusão definitiva de conta e dados (DPO: `contatocreativeam@gmail.com`).
4. **Independência e Jurisprudência (LPI Art. 132, IV e TJSP):**
   - Explicada a menção ao precedente do TJSP (AI nº 2207360-65.2023.8.26.0000 - StopClub) como exemplo jurisprudencial paradigmático sobre o direito de uso de calculadoras sobrepostas para motoristas autônomos, sem caracterizar salvo-conduto ou imunidade irrestrita.

---

## 2. Links Oficiais e Painéis

| Recurso | URL | Status |
|---|---|---|
| **Produção Web (Vercel)** | `https://goorno-app.vercel.app` | ✅ No Ar & Atualizado |
| **Central de Continuidade** | `http://localhost:3010` | ✅ Checkpoint #014 Ativo |
| **Copiloto Web Local** | `http://localhost:3008` | ✅ Ativo na porta 3008 |
| **Repositório GitHub** | `https://github.com/olirumtecapp-debug/goorno.git` | ✅ Branch main sincronizada |

---
*Checkpoint #014 registrado e consolidado em 24/10/2026.*
