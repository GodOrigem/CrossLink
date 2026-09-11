# Changelog

## 1.2.1
- `/plink resetprompt <jogador>` faz o convite automático do Bedrock reaparecer

## 1.2.0
- **Corrige perda de inventário ao vincular.** A varredura elegia como fonte da
  verdade qualquer conta que diferisse do estado do grupo — e uma conta
  recém-vinculada sempre difere. Um inventário Bedrock vazio sobrescrevia um
  inventário Java cheio. Agora cada jogador tem o próprio retrato anterior, e
  só vira fonte quem mudou em relação a si mesmo.
- **Corrige duplicação ao remover o plugin.** A conta secundária agora sai com a
  playerdata vazia; antes os itens existiam em dois arquivos ao mesmo tempo.
- Grupos passam a ter conta primária, definida como a conta Java no vínculo
- Backup automático antes de cada escrita destrutiva, com `/plink backups` e
  `/plink restore`
- `/plink primary` para trocar a conta dona dos dados
- Pets passam a reconhecer a conta que estiver online

## 1.1.1
- **Corrige formulário travado.** Labels também ocupam índice na resposta do
  Cumulus, então `asInput(0)` lia o label e estourava `IllegalStateException`.
  A exceção morria dentro do handler e o jogador ficava sem receber o código.
- Formulário do código não era exibido: era enviado no mesmo instante em que o
  anterior fechava, e o cliente Bedrock só mostra um por vez
- Handlers passam a ser envolvidos por um guard que loga e avisa o jogador

## 1.1.0
- Vínculo self-service com formulário nativo do Bedrock
- Cópia da skin da conta Java para a Bedrock
- Convite automático no primeiro login, uma vez por conta

## 1.0.0
- Compartilhamento de inventário, ender chest e XP entre contas vinculadas
- Comandos de admin `/plink`
