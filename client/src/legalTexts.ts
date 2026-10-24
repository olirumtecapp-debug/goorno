/**
 * Textos Jurídicos Oficiais e Termos de Uso do GoOrNo
 * Harmonizados com a Lei Geral de Proteção de Dados (LGPD - Lei nº 13.709/2018),
 * Código de Defesa do Consumidor (CDC - Lei nº 8.078/1990),
 * Marco Civil da Internet (Lei nº 12.965/2014) e
 * Lei de Propriedade Industrial (Art. 132, IV da Lei nº 9.279/1996).
 *
 * Responsável: Murilo Ferreira da Silva (Pessoa Física, Desenvolvedor Independente).
 * Projeto / Marca de Contato: CreativeAM.
 */

export const LEGAL_METADATA = {
  version: "1.3",
  lastUpdated: "24 de Outubro de 2026",
  appName: "GoOrNo",
  appCategory: "Ferramenta Auxiliar de Cálculo e Copiloto de Rentabilidade para Motoristas",
  responsiblePerson: "Murilo Ferreira da Silva",
  responsibleStatus: "Pessoa Física / Desenvolvedor Independente",
  projectName: "CreativeAM",
  contactEmail: "contatocreativeam@gmail.com",
  privacyChannel: "contatocreativeam@gmail.com",
  websiteUrl: "https://goorno-app.vercel.app",
};

export const INDEPENDENCE_DISCLAIMER = {
  title: "Natureza Jurídica, Independência e Funcionamento do GoOrNo",
  shortBadge: "Aplicativo Independente",
  summary:
    "O GoOrNo é uma ferramenta independente de apoio à análise de rentabilidade de corridas desenvolvida por Murilo Ferreira da Silva (desenvolvedor independente). Sua finalidade é estritamente auxiliar o motorista autônomo no cálculo econômico de ofertas exibidas na tela, de acordo com parâmetros por ele mesmo definidos.",
  fullText: `NATUREZA JURÍDICA, INDEPENDÊNCIA E FUNCIONAMENTO DO GOORNO

1. IDENTIFICAÇÃO DO PROJETO E DO DESENVOLVEDOR
O GoOrNo é um software idealizado, desenvolvido e disponibilizado por Murilo Ferreira da Silva, pessoa física e desenvolvedor independente, utilizando CreativeAM como denominação de projeto e marca de contato, com suporte pelo e-mail contatocreativeam@gmail.com.

2. NATUREZA DO SERVIÇO
O GoOrNo é uma ferramenta tecnológica independente de apoio à produtividade e análise de rentabilidade operacional para motoristas autônomos de plataformas de mobilidade. Sua finalidade é auxiliar o trabalhador na tomada de decisões econômicas ao estimar ganho por quilômetro (R$/km), ganho por hora (R$/h) e resultado financeiro projetado com base nos parâmetros que o próprio usuário insere (consumo do veículo, valor do combustível, custo de manutenção e metas pretendidas).

3. AUTONOMIA TOTAL DO MOTORISTA E AUSÊNCIA DE AÇÕES AUTOMATIZADAS
O GoOrNo NÃO é um robô de aceitação ou recusa de chamadas. 
- O aplicativo JAMAIS clica, toca, aceita, rejeita ou cancela corridas de maneira automática em nome do usuário.
- O resultado visual exibido na tela (Semáforo Verde, Amarelo ou Vermelho) constitui mera estimativa indicativa para consulta rápida.
- A decisão de aceitar ou rejeitar uma viagem, a verificação das condições de trânsito, clima, itinerário e segurança pública permanecem 100% sob a discricionariedade, discernimento e responsabilidade exclusiva do motorista.

4. INDEPENDÊNCIA EM RELAÇÃO A PLATAFORMAS DE TRANSPORTE DE TERCEIROS
O GoOrNo é um software de desenvolvimento autônomo e independente. Não mantém vínculo societário, parceria formal, patrocínio, chancela ou contratação com a Uber Technologies, Inc., a 99 Tecnologia Ltda. ou quaisquer outras empresas operadoras de transporte por aplicativo.
As menções aos nomes comerciais "Uber" e "99" destinam-se exclusivamente à identificação da compatibilidade técnica do sistema com os aplicativos em questão, caracterizando uso nominativo legítimo e de boa-fé, em estrita conformidade com o Artigo 132, Inciso IV, da Lei de Propriedade Industrial (Lei Federal nº 9.279/1996). Todas as marcas registradas pertencem exclusivamente aos seus respectivos titulares.

5. PRECEDENTES JUDICIAIS E LIMITES DA INTERPRETAÇÃO JURÍDICA
A utilização de aplicativos auxiliares e sobrepostos de cálculo para motoristas de transporte privado já foi objeto de análise pelo Poder Judiciário brasileiro, destacando-se como paradigma o julgamento proferido pela 2ª Câmara Reservada de Direito Empresarial do Tribunal de Justiça do Estado de São Paulo (TJSP) no Agravo de Instrumento nº 2207360-65.2023.8.26.0000 (caso relativo à ferramenta StopClub).
Naquele julgamento colegiado, restou consignado o entendimento de que ferramentas computacionais destinadas a auxiliar trabalhadores autônomos no cálculo de custos e produtividade, mediante leitura local de dados na tela do aparelho e sem violação de sistemas internos ou apropriação indevida de dados sigilosos das operadoras, inserem-se na livre iniciativa, livre concorrência e direito de informação do trabalhador.
Ressalta-se, contudo, que o desenvolvedor do GoOrNo não foi parte naquele litígio específico e tal precedente constitui referencial jurisprudencial para o setor, sem caracterizar outorga de salvo-conduto ou imunidade jurídica irrestrita.

6. TRATAMENTO LOCAL DE DADOS E RESPEITO À PRIVACIDADE
A leitura das ofertas na tela do aparelho do usuário opera de forma prioritariamente local e efêmera na memória volátil (RAM) do dispositivo, através dos recursos autorizados do sistema operacional Android (AccessibilityService e/ou projeção de mídia para OCR).
O GoOrNo não solicita senhas, não acessa mensagens privadas, não monitora aplicativos de bancos e não realiza mineração de dados pessoais de passageiros ou usuários finais.`,
};

export const HOW_OFFER_READING_WORKS = {
  title: "Como Funciona a Leitura de Ofertas de Corrida",
  summary:
    "O GoOrNo funciona como uma calculadora de tela: interpreta temporariamente na memória RAM os dados da chamada (valor em R$, distância em km, duração em minutos e bairros) para calcular o seu lucro real. Nunca clica sozinho e não armazena dados de passageiros.",
  bulletPoints: [
    {
      title: "Processamento Efêmero e Local na RAM",
      desc: "Os dados da oferta de viagem (valor em R$, km e tempo) são lidos e processados localmente no seu aparelho no momento exato em que a chamada surge na tela, sem depender de servidores remotos para o cálculo imediato.",
    },
    {
      title: "Zero Acesso a Senhas ou Contas",
      desc: "O GoOrNo não solicita, não intercepta e não armazena senhas, tokens de autenticação ou dados bancários das suas contas na Uber ou na 99.",
    },
    {
      title: "Controle Manual Exclusivo (Sem Autoclique)",
      desc: "O aplicativo não possui funcionalidades de clique automático. Toda interação com os botões de aceite ou rejeição das plataformas de mobilidade permanece sob o controle físico e manual do motorista.",
    },
    {
      title: "Proteção da Privacidade de Terceiros",
      desc: "Dados sensíveis ou identificadores diretos de passageiros (como fotos, sobrenomes completos, telefones ou documentos) não são extraídos, comercializados nem enviados a terceiros.",
    },
    {
      title: "Recurso Auxiliar de Street View",
      desc: "Ao acionar o botão de visualização de endereço, o GoOrNo tão somente repassa a informação textual do endereço para o aplicativo oficial Google Maps do aparelho via Intent do Android.",
    },
  ],
};

export const TERMS_OF_USE_TEXT = `TERMOS DE USO DO GOORNO
Versão 1.3 — Atualizada em 24 de outubro de 2026

Bem-vindo ao GoOrNo! Estes Termos de Uso regulam as condições de acesso e utilização do aplicativo móvel GoOrNo e do portal oficial https://goorno-app.vercel.app.

1. IDENTIFICAÇÃO DO DESENVOLVEDOR RESPONSÁVEL
O aplicativo GoOrNo e seus serviços correlatos são desenvolvidos e disponibilizados por:
- Desenvolvedor Responsável: Murilo Ferreira da Silva, pessoa física, atuando de maneira independente.
- Nome do Projeto / Identificação de Contato: CreativeAM.
- Canal Oficial de Atendimento, Suporte e Dúvidas: contatocreativeam@gmail.com
- Canal de Privacidade e Proteção de Dados: contatocreativeam@gmail.com
(Nota de Transparência: O projeto é conduzido de forma autônoma e independente por pessoa física, sem pessoa jurídica formalmente constituída no momento).

2. OBJETO E NATUREZA DO SERVIÇO
2.1. O GoOrNo consiste em um aplicativo de apoio tecnológico destinado a auxiliar motoristas autônomos no cálculo matemático de estimativas de rentabilidade e custos operacionais de viagens ofertadas por plataformas de mobilidade.
2.2. A partir dos dados informados pelo motorista (tais como autonomia do veículo em km/l, preço do combustível, reserva para manutenção preventiva e metas financeiras), o sistema calcula estimativas de receita por quilômetro (R$/km), receita horária (R$/h) e lucro líquido estimado.
2.3. LIMITES DOS RESULTADOS CALCULADOS:
    a) As projeções financeiras exibidas representam estimativas matemáticas baseadas nos dados captados na tela no instante da oferta e nos custos parametrizados pelo próprio usuário;
    b) Variações reais de trajeto, desvios no trânsito, tempos de espera não computados, cobranças de pedágios ou imprevistos mecânicos podem produzir resultados práticos divergentes das estimativas preliminares;
    c) O GoOrNo NÃO garante renda mínima, faturamento fixo, volume mínimo de corridas, nem atesta a segurança física ou imunidade criminal em determinadas rotas ou localidades.

3. AUTONOMIA PROFISSIONAL E RESPONSABILIDADE DO MOTORISTA
3.1. O GoOrNo não substitui o tirocínio, a cautela e a responsabilidade profissional do condutor.
3.2. A decisão de aceitar, rejeitar, aguardar ou cancelar viagens compete unicamente ao motorista. O GoOrNo não aceita nem rejeita corridas de maneira automatizada.
3.3. O motorista é o único e exclusivo responsável pela condução segura de seu veículo, pela observância rigorosa das normas do Código de Trânsito Brasileiro (Lei nº 9.503/1997) e pelo acondicionamento correto do smartphone em suporte veicular apropriado.

4. RECURSO DE MAPAS E STREET VIEW
4.1. A facilidade de visualização de endereços no Street View/Google Maps é acionada sob demanda do motorista.
4.2. As fotografias e dados geográficos são providos por serviços de terceiros (Google LLC) e refletem registros históricos, não constituindo garantia contemporânea sobre a segurança pública de qualquer endereço.

5. RELAÇÃO INDEPENDENTE COM PLATAFORMAS DE TRANSPORTE
5.1. Uber Technologies, Inc. e 99 Tecnologia Ltda. são entidades autônomas e titulares de suas respectivas marcas e termos contratuais.
5.2. O GoOrNo não possui associação comercial, patrocínio ou representação perante as referidas empresas. As referências têm fins meramente indicativos de compatibilidade operacional nos termos do Art. 132, IV da Lei nº 9.279/1996.

6. CONTA, CREDENCIAIS E TRANSFERÊNCIA DE APARELHO
6.1. A identificação da conta do usuário é realizada por meio de:
    a) Número de telefone (WhatsApp);
    b) Código PIN de 4 dígitos cadastrado pelo próprio motorista;
    c) Palavra-chave secreta ou placa do veículo utilizada como mecanismo auxiliar para recuperação do PIN em caso de esquecimento.
6.2. A placa do veículo ou palavra-chave secreta funciona como parâmetro secundário de validação e não deve ser compartilhada com terceiros.
6.3. Cada licença de uso é de caráter pessoal e intransferível, autorizada para utilização no aparelho ativo do motorista. A fim de resguardar o sistema contra pirataria e comercialização indevida de acessos, trocas simultâneas e anormais de dispositivo são monitoradas tecnicamente.

7. MODELO DE CONTRATAÇÃO, PREÇOS E DIREITO DE ARREPENDIMENTO (CDC ART. 49)
7.1. O GoOrNo disponibiliza um período de teste gratuito de 7 (sete) dias para novos usuários avaliarem as funcionalidades do sistema.
7.2. O acesso regular pode ser contratado através de planos pré-pagos de acesso com vigência determinada (30, 90, 180 ou 365 dias), com pagamentos efetuados exclusivamente via PIX através da instituição de pagamento parceira (Asaas Gestão Financeira S.A.).
7.3. Como o pagamento é realizado via PIX avulso por período contratado, NÃO há cobrança automática em cartão de crédito nem renovação tácita não solicitada. A continuidade do serviço ao término do prazo depende de nova autorização e pagamento deliberado pelo motorista.
7.4. DIREITO LEGAL DE ARREPENDIMENTO: Em estrita observância ao Artigo 49 do Código de Defesa do Consumidor (Lei Federal nº 8.078/1990), o consumidor poderá exercer o direito de arrependimento e solicitar o cancelamento com reembolso integral de qualquer plano contratado no prazo de até 7 (sete) dias corridos a contar da confirmação do pagamento.
7.5. Para exercer o direito de arrependimento e receber o estorno via chave PIX, o usuário deve enviar solicitação ao canal contatocreativeam@gmail.com ou pelo suporte do aplicativo com o número do telefone cadastrado e o comprovante do PIX.

8. DISPONIBILIDADE, ATUALIZAÇÕES E LIMITAÇÃO DE RESPONSABILIDADE
8.1. O desenvolvedor empenhará seus melhores esforços para manter o aplicativo funcional, seguro e compatível com as versões recentes do sistema Android.
8.2. O GoOrNo não poderá ser responsabilizado por interrupções temporárias decorrentes de falhas na operadora de telefonia celular do usuário, instabilidade de sinal GPS, modificações estruturais unilaterais promovidas por terceiros em seus respectivos aplicativos que exijam atualização técnica corretiva, ou motivos de força maior.

9. FORO E LEGISLAÇÃO APLICÁVEL
Estes Termos são regidos pelas leis da República Federativa do Brasil. Para a solução de controvérsias decorrentes deste contrato, fica eleito o foro do domicílio do consumidor, ressalvadas as disposições específicas da legislação aplicável.`;

export const PRIVACY_POLICY_TEXT = `POLÍTICA DE PRIVACIDADE DO GOORNO
Versão 1.3 — Adequação Integral à Lei Geral de Proteção de Dados (LGPD - Lei Federal nº 13.709/2018)
Última Atualização: 24 de outubro de 2026

O GoOrNo preza pelo respeito à privacidade, pela transparência no tratamento de informações e pelo sigilo dos dados de seus usuários. Esta Política descreve como os dados são coletados, tratados, armazenados e protegidos.

1. IDENTIFICAÇÃO DO CONTROLADOR E CANAL DE PRIVACIDADE
- Controlador dos Dados Pessoais: Murilo Ferreira da Silva, pessoa física, desenvolvedor independente responsável pelo projeto GoOrNo / CreativeAM.
- Canal de Atendimento para Assuntos de Privacidade e Dados (LGPD): contatocreativeam@gmail.com
(Nota de Transparência: Conforme as normas vigentes da Autoridade Nacional de Proteção de Dados - ANPD aplicáveis a agentes de tratamento de pequeno porte e pessoas naturais desenvolvedoras, o atendimento ao titular é exercido diretamente através do canal oficial de e-mail informado).

2. PRINCÍPIO DO PROCESSAMENTO LOCAL E INVENTÁRIO REAL DE DADOS

2.1. REGRA GERAL: PROCESSAMENTO LOCAL NO DISPOSITIVO
O GoOrNo foi arquitetado com base no princípio do "Privacy by Design" (Privacidade desde a Concepção). Todas as leituras e interpretações de telas de ofertas de corrida ocorrem localmente no processador e na memória RAM do seu smartphone.

2.2. DADOS CADASTRAIS E DE LICENÇA (Armazenados no Servidor para Gestão do Acesso)
- Número de Telefone (WhatsApp): Utilizado como chave unívoca de identificação da licença, controle do período de teste gratuito e prevenção contra fraudes de duplicidade.
- Código PIN (4 dígitos): Utilizado para autenticar a titularidade da conta em caso de troca de aparelho. O PIN é armazenado em formato criptográfico com hash seguro (SHA-256 com salt), impedindo visualização em texto puro.
- Palavra-Chave Secreta ou Placa do Veículo: Utilizada exclusivamente como fator de segurança adicional para recuperação autônoma do PIN pelo próprio motorista.
- Identificador de Instalação (Android ID ou UUID): Gerado localmente para vincular a licença ativa ao aparelho em uso, impedindo o uso simultâneo não contratado da mesma licença por múltiplos aparelhos.

2.3. PARÂMETROS ECONÔMICOS E PREFERÊNCIAS (Armazenados Localmente no SQLite)
- Dados do veículo (modelo, consumo em km/l, preço do combustível, custo de manutenção preventiva por km).
- Metas financeiras (valores mínimos e desejados de R$/km e R$/hora, margem amarela de tolerância).
- Regiões e bairros cadastrados como áreas de atenção ou bairros preferenciais.
Armazenamento: Salvos localmente no banco de dados SQLite do próprio smartphone do usuário.
Sincronização em Nuvem (Opcional): Se e somente se o motorista optar conscientemente por ativar a opção "Consentimento de Backup em Nuvem" nas configurações do aplicativo, esses parâmetros econômicos serão enviados de forma segura para o repositório em nuvem para viabilizar a restauração em caso de troca de smartphone.

2.4. DADOS DE OFERTAS DE CORRIDAS (Tratamento Efêmero)
- Informações captadas durante a oferta: Valor monetário da corrida (R$), quilometragem total e parcial (km), duração estimada (minutos), nota de avaliação do passageiro e logradouros gerais de embarque e desembarque.
- Natureza do Tratamento: O processamento é efêmero na memória RAM.
- Não retenção de dados de passageiros: O GoOrNo NÃO coleta nem armazena nomes completos de passageiros, fotografias, telefones, documentos de identificação ou dados pessoais dos passageiros. O histórico local de chamadas no aparelho registra apenas os valores numéricos econômicos e o resumo do logradouro para fins de estatística do motorista.

3. PERMISSÕES ESPECÍFICAS DO ANDROID E SUAS FINALIDADES
O funcionamento das funcionalidades auxiliares requer a concessão de permissões específicas no sistema Android:
- Serviço de Acessibilidade (AccessibilityService): Utilizado única e exclusivamente para identificar o surgimento de cartões de viagens nas telas dos aplicativos de transporte compatíveis e ler o texto correspondente para alimentar a calculadora de rentabilidade. O serviço NÃO registra digitação de senhas, não clica na tela e não monitora aplicativos externos.
- Sobreposição a Outros Aplicativos (SYSTEM_ALERT_WINDOW): Necessária para desenhar o semáforo e card flutuante sobreposto à tela do aplicativo de transporte.
- Serviço de Primeiro Plano (FOREGROUND_SERVICE): Necessário para manter o processo de cálculo acordado enquanto o motorista utiliza outros aplicativos ou navega no GPS, sujeito às políticas e gerenciamento de energia do sistema operacional Android.
- Captura de Tela / Projeção de Mídia (OCR): Quando o modo de leitura por reconhecimento visual estiver ativado, processa localmente a imagem do cartão da oferta via OCR (ML Kit local no aparelho) e descarta o bitmap imediatamente da memória RAM após a leitura.

4. COMPARTILHAMENTO DE DADOS COM TERCEIROS
O GoOrNo não vende, não aluga e não compartilha dados de seus usuários com agências de publicidade, corretores de dados, empresas de seguro ou terceiros estranhos à prestação do serviço.
O compartilhamento restringe-se aos operadores técnicos estritamente necessários para viabilizar o serviço:
- Asaas Gestão Financeira S.A.: Instituição de pagamento parceira, destinatária do número de telefone e valor da transação para geração da cobrança por PIX e conciliação bancária.
- Provedor de Nuvem Seguro (Google Cloud Firestore): Utilizado exclusivamente para guarda dos registros de licença e cópias de backup autorizadas pelos usuários que concederam consentimento expresso.
- Google Maps (Google LLC): Ao tocar expressamente no botão de Street View, o endereço público da rota é repassado ao aplicativo Google Maps nativo instalado no smartphone.

5. SEGURANÇA E RETENÇÃO DAS INFORMAÇÕES
5.1. O tráfego de dados entre o aplicativo e os servidores é protegido por conexão segura HTTPS / TLS de padrão de mercado.
5.2. O código PIN do usuário é armazenado com derivação criptográfica (hash SHA-256 com salt), impedindo armazenamento em texto puro.
5.3. As informações de licença permanecem armazenadas enquanto a conta do usuário estiver ativa ou até que seja exercido o direito de exclusão.

6. DIREITOS DO TITULAR DOS DADOS (ARTIGO 18 DA LGPD)
O usuário, na condição de titular de dados pessoais, tem o direito de solicitar a qualquer momento:
I - A confirmação da existência de tratamento e acesso aos seus dados;
II - A correção de dados incompletos, inexatos ou desatualizados;
III - A anonimização, bloqueio ou eliminação de dados desnecessários ou tratados em desconformidade com a LGPD;
IV - A portabilidade dos dados;
V - A eliminação dos dados pessoais tratados com o consentimento do titular;
VI - A revogação do consentimento concedido.

7. PROCEDIMENTO REAL DE EXCLUSÃO DE CONTA E DADOS
O GoOrNo disponibiliza canais diretos e efetivos para exclusão da conta e dos dados:
- Pelo Aplicativo: Na tela de "Privacidade e Backup", o usuário pode a qualquer momento revogar o consentimento de backup na nuvem ou acionar a exclusão definitiva dos dados locais.
- Pelo Canal de Atendimento / Web: O usuário pode enviar solicitação com o assunto "Exclusão de Dados LGPD" para o e-mail contatocreativeam@gmail.com informando seu número de WhatsApp cadastrado. A exclusão de registros do Firestore e de históricos vinculados será processada no prazo legal, ressalvadas as hipóteses de guarda obrigatória por dever legal ou regulatório.

8. ALTERAÇÕES DESTA POLÍTICA
Esta Política de Privacidade poderá sofrer atualizações visando ao aprimoramento técnico dos serviços ou à conformidade com novas diretrizes regulatórias. Modificações substanciais serão comunicadas aos usuários por meio do aplicativo ou pelo portal web com a respectiva indicação da data de revisão.`;
