# GoOrNo — protótipo de redesign

Protótipo estático da landing page do GoOrNo, preparado para continuar no Antigravity.

## Abrir

Abra `index.html` diretamente no navegador ou rode um servidor local:

```bash
python3 -m http.server 4173
```

Depois acesse `http://localhost:4173`.

## Estrutura

- `index.html` — estrutura, copy e seções da landing page.
- `styles.css` — sistema visual responsivo.
- `script.js` — menu mobile, FAQ e entrada suave das seções.
- `assets/` — logo e duas telas reais reaproveitadas da página atual.

## Direção do redesign

A proposta usa o conceito de **painel de decisão do motorista**: o produto aparece como uma ferramenta operacional, rápida e confiável. A primeira dobra prioriza a promessa “Aceite só as corridas que dão lucro”, o benefício é demonstrado com uma comparação aceitar/recusar e o APK/permissões fica depois da prova de valor.

### Paleta

- Verde profundo: confiança, foco e operação.
- Lima: assinatura própria da marca e ação principal.
- Verde claro: corrida boa / aprovação.
- Coral: corrida ruim / alerta.
- Off-white: leitura confortável e contraste com o dark mode.

### Próximos passos sugeridos

1. Trocar os links `#download` pelo endpoint real de download do APK.
2. Inserir todas as telas da galeria atual em um carrossel ou lightbox.
3. Validar a copy e os números com a equipe do produto.
4. Adicionar eventos de analytics nos CTAs e no clique da galeria.
5. Conectar o botão “Fale conosco” ao canal oficial.
6. Ajustar o conteúdo de segurança e permissões conforme a política vigente do Android.

## Observação sobre assets

O protótipo reutiliza assets públicos já presentes em `goorno-app.vercel.app`, apenas para facilitar a continuidade visual. Substitua-os pelos arquivos oficiais do projeto quando for integrar a versão final.
