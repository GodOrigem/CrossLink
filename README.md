# LinkedPlayers

Vincula uma conta **Bedrock** e uma **Java** no mesmo personagem: inventário,
ender chest, XP, pets e skin compartilhados — **inclusive com as duas online ao
mesmo tempo**.

> ### ⚠️ Status: não mantido ativamente
>
> Este plugin foi escrito para um servidor específico e é publicado porque pode
> ser útil para outras pessoas. **Não há garantia de atualizações, correções ou
> suporte.** Issues e pull requests podem demorar ou não ser respondidos.
>
> O código é MIT: sinta-se à vontade para forkar, modificar e publicar sua
> própria versão. Se você mantiver um fork ativo, abra uma issue que eu aponto
> para ele aqui.
>
> Testado em **Paper 26.2** com **Floodgate 2.2.5** e **Geyser 2.11.2**.
> Versões diferentes podem exigir ajustes.

---

## O problema que ele resolve

O Floodgate já tem linking nativo, e **ele é melhor se você não precisar das
duas contas online juntas**: a conta Bedrock passa a *ser* a Java, mesma UUID,
tudo compartilhado sem plugin nenhum. Se esse é o seu caso, use o Floodgate e
ignore este projeto.

O problema é esse "mesma UUID": uma UUID é uma sessão. Ao entrar com a segunda
conta, o servidor derruba a primeira com *"You logged in from another
location"*.

Este plugin mantém as UUIDs separadas e espelha o estado entre elas. É o que
permite, por exemplo, deixar o personagem numa fazenda pelo celular enquanto
joga no PC com a mesma conta.

## Requisitos

| | |
|---|---|
| Servidor | Paper 26.2 (ou compatível) |
| Java | 25 |
| Floodgate | opcional — sem ele, o vínculo funciona só por comando de texto |

## Instalação

1. Baixe o `LinkedPlayers-x.y.z.jar` em [Releases](../../releases)
2. Coloque em `plugins/`
3. Reinicie o servidor

Não há configuração obrigatória. O `config.yml` é gerado no primeiro boot.

## Como o jogador vincula

Self-service, sem admin.

**No Bedrock**, um formulário nativo aparece no primeiro login perguntando se
quer vincular. Ele aparece **uma única vez por conta**: quem recusar não é mais
incomodado, e só volta a ver rodando `/link`. Um admin pode reabrir com
`/plink resetprompt <jogador>`.

O fluxo tem duas etapas, e isso é de propósito — é o que prova que a mesma
pessoa controla as duas contas:

```
Na conta A:  /link <nick da conta B>     → recebe um código de 6 dígitos
Na conta B:  /link <código>              → vinculado
```

Sem essa confirmação cruzada, qualquer um se vincularia ao inventário alheio
digitando o nick da vítima.

| Comando | O que faz |
|---|---|
| `/link` | abre o formulário (Bedrock) ou mostra a ajuda (Java) |
| `/link <nick>` | inicia o vínculo com essa conta |
| `/link <código>` | confirma um vínculo pendente |
| `/link status` | mostra o vínculo atual |

### Por que não login da Microsoft

Seria o jeito canônico de provar posse da conta Java, mas exige registrar uma
aplicação no Azure AD e obter acesso ao escopo da API do Minecraft — e **cada
pessoa que hospedasse o plugin teria que fazer o próprio cadastro**. O código
cruzado resolve o mesmo problema sem infraestrutura externa.

## Comandos de admin

Exigem a permissão `linkedplayers.admin` (padrão: op).

| Comando | O que faz |
|---|---|
| `/plink create <grupo>` | cria um grupo vazio |
| `/plink add <grupo> <jogador\|uuid>` | adiciona alguém ao grupo |
| `/plink remove <grupo> <jogador\|uuid>` | tira alguém do grupo |
| `/plink primary <grupo> <jogador>` | define a conta dona dos dados |
| `/plink sync <jogador>` | força este como fonte da verdade |
| `/plink backups <jogador>` | lista os backups disponíveis |
| `/plink restore <jogador> [arquivo]` | restaura um backup |
| `/plink resetprompt <jogador>` | faz o convite do Bedrock reaparecer |
| `/plink list` | lista grupos e membros |
| `/plink delete <grupo>` | apaga o grupo (ninguém perde itens) |
| `/plink reload` | recarrega config e grupos |

Qualquer número de grupos, qualquer número de membros por grupo — não está
limitado a pares Bedrock/Java.

### Adicionando conta Bedrock offline

`/plink add <grupo> .NickBedrock` **não funciona com o jogador offline**, e isso
é proposital. Com `online-mode=true`, resolver nome offline consultaria a
Mojang, onde uma conta Floodgate não existe: voltaria uma UUID errada e o
vínculo apontaria para o nada, sem erro nenhum.

Peça para o jogador entrar, ou passe a UUID Floodgate direto — elas começam com
`00000000-0000-0000-`.

## Conta primária

Todo grupo tem uma **conta primária** — na prática a conta Java, porque é a
única que existe na Mojang. É nela que a playerdata de verdade fica; as
secundárias são espelho.

Duas regras decorrem disso, e as duas existem por causa de bugs reais:

**Quem entra adota, nunca sobrescreve.** Uma versão anterior tratava como
"fonte da verdade" qualquer conta cujo estado diferisse do estado do grupo — o
que inclui, sempre, uma conta recém-vinculada. O resultado foi um inventário
Bedrock vazio apagando um inventário Java cheio. Agora uma conta só vira fonte
quando mudou em relação ao **próprio retrato anterior**.

**A secundária sai com a playerdata vazia.** Sem isso os itens existiriam em
dois arquivos de jogador ao mesmo tempo, e bastaria remover o plugin para cada
conta acordar com uma cópia — duplicando tudo. Com `clear-secondary-on-quit`
ligado, remover o plugin deixa exatamente um dono.

## Backups

Antes de qualquer escrita destrutiva, o estado anterior vai para
`plugins/LinkedPlayers/backups/<uuid>/<timestamp>.yml` (os 10 últimos por
padrão).

```
/plink backups Origem_
/plink restore Origem_
/plink restore Origem_ 1757600000000.yml
```

Restaurar também gera backup, então dá para desfazer a restauração.

## Configuração

```yaml
sync:
  inventory: true      # mochila + armadura + offhand
  ender-chest: true
  xp: true             # nível, progresso e total
  pets: true           # lobo, gato, cavalo, papagaio
  health: false        # ver aviso abaixo
  food: false

safety:
  clear-secondary-on-quit: true   # evita duplicação ao remover o plugin
  backups-to-keep: 10

link:
  prompt-on-first-join: true      # formulário automático no Bedrock
  prompt-delay-ticks: 60
  code-timeout-seconds: 300
  copy-java-skin: true

sweep-interval-ticks: 20          # varredura de segurança (20 = 1 segundo)
save-interval-ticks: 6000
```

## Skin

Ao vincular, a skin da conta Java é aplicada na conta Bedrock. A textura é
buscada no sessionserver da Mojang **com assinatura** (`unsigned=false`) — sem
a assinatura o cliente rejeita a textura e o jogador aparece com a skin padrão.

## Pets

Lobo, gato, cavalo e papagaio passam a reconhecer a conta que estiver online.
Um pet só tem um dono, então a transferência só acontece quando há **apenas um
membro do grupo online** — com os dois conectados não há como decidir, e nada
muda.

## Como a sincronização funciona

Dois caminhos alimentam o espelhamento:

1. **Eventos** — clique em inventário, drop, pickup, quebra de item, colocar
   bloco, comer, XP, dano. Marca quem agiu e propaga no tick seguinte, já com o
   efeito aplicado.
2. **Varredura periódica** (1s por padrão) — compara o retrato de cada membro
   online com o **próprio retrato anterior** e propaga quem mudou. Rede de
   segurança para o que nenhum evento cobriu.

Um conjunto `applying` evita laço infinito: enquanto o plugin escreve no
inventário de alguém, os eventos que isso dispara são ignorados.

Morte tem tratamento próprio. O inventário do morto é esvaziado *depois* do
evento, então o sync é adiado um tick — sem isso os itens cairiam no chão **e**
continuariam no inventário espelhado do outro, duplicando.

## Limites conhecidos

**Vida e fome vêm desligados.** Com `health: true`, dano em um aparece no outro
na hora e morte mata os dois. Ligue só se for isso mesmo que você quer.

**Ação simultânea conflitante pode perder item.** Se as duas contas mexerem no
inventário dentro do mesmo intervalo de varredura, uma mudança vence e a outra
se perde. Alternando entre as contas não aparece; as duas organizando baús ao
mesmo tempo, aparece. É inerente a inventário compartilhado.

**Não compartilha:** posição, dimensão, advancements, efeitos de poção,
gamemode. Cada conta continua sendo uma entidade própria no mundo.

## Build

```bash
./gradlew build
```

Precisa de JDK 25. O jar sai em `build/libs/`.

Todas as dependências são `compileOnly` — nada de terceiros vai embutido no
jar.

### Estrutura

| Arquivo | Responsabilidade |
|---|---|
| `LinkedPlayersPlugin` | ciclo de vida, config, ligação das peças |
| `SyncEngine` | captura, aplica e decide quem é a fonte da verdade |
| `SyncListener` | eventos que marcam "alguém mexeu" |
| `LinkService` | fluxo de vínculo em duas etapas |
| `LinkGroup` / `GroupManager` | modelo e persistência dos grupos |
| `SharedState` | o que é compartilhado, e sua serialização |
| `BedrockUi` | formulários nativos (só carrega se houver Floodgate) |
| `SkinService` | busca e aplica a skin da conta Java |
| `PromptTracker` | quem já viu o convite automático |
| `LinkCommand` / `PlayerLinkCommand` | `/plink` e `/link` |

## Licença

MIT. Veja [LICENSE](LICENSE).

Desenvolvido com a ajuda do [Claude Code](https://claude.com/claude-code).
