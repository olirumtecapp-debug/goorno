# GoOrNo — plano de continuidade no Antigravity

## Objetivo

Transformar a landing page do GoOrNo em uma página de conversão mais clara para motoristas de Uber e 99. A nova página deve responder rapidamente a três perguntas:

1. O que o GoOrNo faz?
2. Como ele ajuda o motorista a ganhar mais?
3. Como posso começar agora?

## Conceito visual escolhido

**Painel de decisão do motorista.** A marca aparece como uma ferramenta operacional: rápida, objetiva e confiável. O visual combina verde profundo de confiança com lima de ação, enquanto verde claro e coral representam as decisões de aceitar e recusar.

### Princípios

- **Decisão antes de explicação:** mostrar o resultado da ferramenta antes de explicar detalhes técnicos.
- **Leitura em poucos segundos:** títulos curtos, números grandes e blocos fáceis de escanear.
- **Produto visível:** usar telas reais do app como prova, não deixar a interface escondida em uma galeria distante.
- **Transparência sobre o APK:** explicar a instalação sem transformar o primeiro contato em um alerta técnico.

## Arquitetura da página

### 1. Header fixo

Logo, âncoras para “Como funciona”, “Telas do app” e “Dúvidas”, mais CTA “Baixar grátis”.

### 2. Hero

Headline: **Aceite só as corridas que dão lucro.**

Subheadline: o app lê a tela da Uber e da 99 e mostra o valor real em menos de 1 segundo.

CTA principal: **Baixar GoOrNo grátis**.

CTA secundário: **Ver o app na prática**.

Prova rápida: 7 dias grátis, sem cartão, Android 8+.

### 3. Prova do fluxo

Três etapas compactas: ler a chamada, calcular o ganho real e decidir rápido.

### 4. Comparação de decisão

Dois cards lado a lado:

- Corrida boa: R$ 2,17/km — aceitar.
- Corrida ruim: R$ 1,25/km — recusar.

### 5. Benefícios

R$/km e R$/hora, veredito em cores, Street View e custo real do veículo.

### 6. Telas reais

Duas telas em composição sobreposta, com legenda de card flutuante e decisão instantânea.

### 7. Instalação

Baixar o APK, permitir a instalação e ativar os 7 dias grátis.

### 8. FAQ

Gratuidade, risco de banimento e aviso do Android sobre APK.

### 9. CTA final e rodapé

Reforçar a ação de download e manter o rodapé enxuto.

## Tokens visuais

```css
--ink: #071d19;
--ink-2: #0d302a;
--lime: #d9ff4f;
--lime-2: #bfe72d;
--paper: #f3f5ed;
--cream: #fbfcf7;
--muted: #7a8982;
--good: #dff4af;
--bad: #ffe1d9;
```

### Tipografia

- **Space Grotesk:** títulos, números e labels de decisão.
- **DM Sans:** texto corrido, navegação e microcopy.

## Responsividade

- Desktop: duas colunas no hero, cards em grid e seções com bastante respiro.
- Tablet: reduzir o hero e preservar os cards lado a lado quando houver espaço.
- Mobile: hero em coluna, CTA com largura total, cards empilhados, menu recolhível e telas sobrepostas em escala menor.

## Tarefas para a próxima etapa

- [ ] Substituir os links de demonstração pelo link real do APK.
- [ ] Adicionar todas as telas da galeria atual em um lightbox/carrossel.
- [ ] Confirmar os números de versão, tamanho do APK e data de lançamento.
- [ ] Confirmar o texto jurídico sobre permissões, acessibilidade e Play Protect.
- [ ] Conectar “Fale conosco” ao canal oficial.
- [ ] Adicionar analytics aos CTAs, FAQ e galeria.
- [ ] Testar o download em Chrome Android, Samsung Internet e navegador padrão.
- [ ] Fazer revisão de acessibilidade: foco de teclado, contraste, alt text e labels.
- [ ] Definir domínio e publicar a versão final.

## Conteúdo que precisa ser validado antes da publicação

- “7 dias grátis” e regras do teste.
- Compatibilidade Android 8+ e Android 14+.
- Se o app funciona atualmente em Uber e 99 sem restrições específicas.
- Valores de R$/km usados como referência.
- Canal oficial de suporte.
- Mensagem atual exibida pelo Play Protect.

## Estrutura entregue

- `index.html`: protótipo navegável.
- `styles.css`: tokens e layout responsivo.
- `script.js`: menu mobile, FAQ e reveal das seções.
- `assets/`: logo e screenshots reais reutilizadas.
- `README.md`: instruções rápidas de execução.
- `PLAN.md`: este handoff de produto e design.
- `assets/current-site-reference.webp`: captura da landing page original para comparação.
- `assets/redesign-preview.webp`: captura da nova prévia para referência visual.
