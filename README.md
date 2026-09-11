# LinkedPlayers

Vincula uma conta **Bedrock** e uma **Java** no mesmo personagem: inventário,
ender chest, XP e skin compartilhados — **inclusive com as duas online ao mesmo
tempo**.

Feito para Paper 26.2 (compilado contra `paper-api:26.2.build.123-stable`),
com interface nativa do Bedrock via Floodgate/Cumulus.

## Por que não usar só o linking do Floodgate

O Floodgate já tem linking nativo, e ele é melhor **se você não precisar das
duas contas online juntas**: a conta Bedrock passa a *ser* a Java, mesma UUID,
tudo compartilhado sem plugin nenhum.

O problema é esse "mesma UUID": uma UUID é uma sessão. Ao entrar com a segunda
conta, o servidor derruba a primeira com *"You logged in from another
location"*. Este plugin mantém as UUIDs separadas e espelha o estado entre elas.

## Como o jogador vincula

Self-service, sem admin.

**No Bedrock**, um formulário nativo aparece no primeiro login perguntando se
quer vincular. Ele aparece **uma única vez por conta**: quem recusar não é mais
incomodado, e só volta a ver rodando `/link`.

O fluxo é em duas etapas, e isso é de propósito — é o que prova que a mesma
pessoa controla as duas contas:

1. Na conta A: `/link <nick da conta B>` → recebe um código de 6 dígitos
2. Na conta B: `/link <código>` → vinculado

Sem essa confirmação cruzada, qualquer um poderia se vincular ao inventário
alheio digitando o nick da vítima.

Outros comandos: `/link status` mostra o vínculo atual.

### E por que não login da Microsoft

Seria o jeito canônico de provar posse da conta Java, mas exige registrar uma
aplicação no Azure AD e obter acesso ao escopo da API do Minecraft — e **cada
pessoa que hospedasse o plugin teria que fazer o mesmo cadastro**. O código
cruzado resolve o mesmo problema sem nenhuma infraestrutura externa.

## Skin

Ao vincular, a skin da conta Java é aplicada na conta Bedrock. A textura é
buscada no sessionserver da Mojang **com assinatura** (`unsigned=false`) — sem
a assinatura o cliente rejeita a textura e o jogador aparece com a skin padrão.

Desligue com `link.copy-java-skin: false`.

## Comandos de admin

Exigem `linkedplayers.admin` (padrão: op).

```
/plink create <grupo>
/plink add <grupo> <jogador|uuid>
/plink remove <grupo> <jogador|uuid>
/plink sync <jogador>          # força este como fonte da verdade
/plink list
/plink delete <grupo>
/plink reload
```

Qualquer número de grupos, qualquer número de membros por grupo — não está
limitado a pares Bedrock/Java.

`/plink add <grupo> .NickBedrock` **não funciona com o jogador offline**, e isso
é proposital. Com `online-mode=true`, resolver nome offline consultaria a
Mojang, onde uma conta Floodgate não existe: voltaria uma UUID errada e o
vínculo apontaria pro nada, sem erro nenhum. Peça pro jogador entrar, ou passe
a UUID Floodgate direto (começam com `00000000-0000-0000-`).

## Como a sincronização funciona

Dois caminhos alimentam o espelhamento:

1. **Eventos** — clique em inventário, drop, pickup, quebra de item, colocar
   bloco, comer, XP, dano. Marca quem agiu e propaga no tick seguinte, já com o
   efeito aplicado.
2. **Varredura periódica** (1s por padrão) — compara o fingerprint de cada
   membro online com o estado do grupo e propaga quem divergiu. Rede de
   segurança para o que nenhum evento cobriu.

Um conjunto `applying` evita laço infinito: enquanto o plugin escreve no
inventário de alguém, os eventos que isso dispara são ignorados.

Morte tem tratamento próprio. O inventário do morto é esvaziado *depois* do
evento, então o sync é adiado um tick — sem isso os itens cairiam no chão **e**
continuariam no inventário espelhado do outro, duplicando.

## Conta primária e por que isso importa

Todo grupo tem uma **conta primária** — na prática a conta Java, porque é a
única que existe na Mojang. É nela que a playerdata de verdade fica; as
secundárias são espelho.

Duas regras decorrem disso, e as duas existem por causa de bugs reais:

**Quem entra adota, nunca sobrescreve.** Uma versão anterior tratava como
"fonte da verdade" qualquer conta cujo estado diferisse do estado do grupo — o
que inclui, sempre, uma conta recém-vinculada. O resultado foi um inventário
Bedrock vazio apagando um inventário Java cheio. Agora uma conta só vira fonte
quando mudou em relação ao **próprio retrato anterior**.

**A secundária sai com a playerdata vazia.** Sem isso, os itens existiriam em
dois arquivos de jogador ao mesmo tempo, e bastaria remover o plugin para cada
conta acordar com uma cópia — duplicando tudo. Com `clear-secondary-on-quit`
ligado, remover o plugin deixa exatamente um dono.

## Backups

Antes de qualquer escrita destrutiva, o estado anterior do jogador vai pra
`backups/<uuid>/<timestamp>.yml` (os 10 últimos por padrão).

```
/plink backups <jogador>
/plink restore <jogador> [arquivo]
```

Restaurar também gera backup, então dá pra desfazer a restauração.

## Pets

Lobo, gato, cavalo e papagaio passam a reconhecer a conta que estiver online.
Um pet só tem um dono, então a transferência só acontece quando há **apenas um
membro do grupo online** — com os dois conectados não há como decidir, e nada
muda. Desligue com `sync.pets: false`.

## Limites que você deve conhecer

**Vida e fome vêm desligados.** Com `health: true`, dano em um aparece no outro
na hora e morte mata os dois. Ligue só se for isso mesmo.

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

Dependências são todas `compileOnly` — nada de terceiros vai embutido no jar.

## Licença

MIT. Veja [LICENSE](LICENSE).
