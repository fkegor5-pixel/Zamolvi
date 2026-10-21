# Zamolvi

[English](../../README.md) · [Русский](README.ru.md) · [Español](README.es.md) · **Português** · [Deutsch](README.de.md) · [Français](README.fr.md) · [Italiano](README.it.md) · [Türkçe](README.tr.md) · [Polski](README.pl.md) · [Українська](README.uk.md) · [العربية](README.ar.md) · [हिन्दी](README.hi.md) · [Bahasa Indonesia](README.id.md) · [中文](README.zh.md) · [日本語](README.ja.md)

**Resposta automática com IA para chats privados do Telegram, rodando no seu próprio servidor.**

O Zamolvi responde aos seus contatos enquanto você está ocupado: recebe uma nova mensagem, envia para a IA que você escolheu e manda a resposta em seu nome — só nos chats privados que você selecionou. O controle é por um app Android e o trabalho roda no seu VPS, então você pode fechar o app e bloquear o telefone.

## Recursos

| Seção | O que faz |
|---|---|
| 1. Conexão ao VPS | Instala e atualiza o servidor no seu VPS por SSH e configura o HTTPS |
| 2. Telegram | Login na sua conta por código ou QR, com senha da nuvem. E também **respostas pelas notificações**, sem login do Telegram no servidor |
| 3. IA e texto | Provedor, modelo, chave, instrução e formatação, teste. E também o **estilo de comunicação**: gentil, atrevido, malandro, sabichão, pirata, computador e outros |
| 4. Chats privados | A quais contatos a resposta automática responde |
| 5. Resposta automática | Ligar e parar, status, horário silencioso, espera por mensagens seguidas |
| 6. Log do servidor | Eventos e diagnóstico |
| 7. Como funciona | Sobre o app e o autor |
| 8. Estatísticas | Respostas, áudios e erros por dia |
| 9. Tema do app | Claro, escuro ou do sistema. E também o **idioma da interface** |
| 10. Mensagens apagadas | Arquivo das mensagens apagadas nos chats escolhidos (até 30 dias), com busca |
| 11. Memória dos contatos | Resumo de cada contato: nome, interesses, combinados, fatos; botão «Esquecer»; carregamento do histórico sob seu comando |
| 12. Fotos e documentos | Texto e descrição de fotos; lê PDF, DOCX, TXT, CSV, JSON |
| 13. Aprovação de respostas | Rascunhos: enviar, editar ou recusar antes do envio |
| 14. Estatísticas detalhadas | Custo da IA, gastos por modelo e chat, tempo de resposta, erros |
| 15. Verificação de recursos | O que funciona, o que não e por quê, com botões de correção |

Também: transcrição de áudios (Whisper via Groq), widget, notificação e bloco nas configurações rápidas, comando `/ai_off` para o contato desligar as respostas, horário silencioso e resposta conjunta a várias mensagens.

## Requisitos

- Android 9 ou mais recente.
- VPS com Debian 12 ou Ubuntu 24.04, IPv4 público e acesso SSH como root.
- Seus próprios `api_id` e `api_hash` do Telegram em [my.telegram.org](https://my.telegram.org) (não são necessários para respostas pelas notificações).
- Uma chave de API de IA: **Groq** (tem plano gratuito), **Google Gemini**, **GigaChat** ou qualquer **API compatível com OpenAI** (por exemplo, OpenRouter).

## Início rápido

1. Baixe o APK em **Releases** e instale.
2. **1. Conexão ao VPS**: informe o endereço e a senha de root e toque em «Instalar o servidor pelo app».
3. **2. Telegram**: informe `api_id`, `api_hash` e o número e entre por código ou QR. Ou abra «Respostas pelas notificações» para usar sem login.
4. **3. IA e texto**: escolha provedor, chave e modelo, escreva a instrução e toque em «Testar». Se quiser, escolha um estilo.
5. **4. Chats privados**: marque seus contatos.
6. **5. Resposta automática**: ligue.

Se algo não funcionar, abra **15. Verificação de recursos**: lá aparecem o motivo e o que fazer.

## Privacidade e uso responsável

- As mensagens vão só para o provedor de IA escolhido e só dos chats escolhidos. Detalhes: [PRIVACY.md](../../PRIVACY.md), [SECURITY.md](../../SECURITY.md).
- Avise seus contatos que uma IA pode responder e não carregue o histórico deles sem consentimento.
- Siga os [termos da API do Telegram](https://core.telegram.org/api/terms): nada de spam ou envios em massa.

Compilação, estrutura do repositório e traduções: veja o [README em inglês](../../README.md).

## Autor e licença

**@stytam** — [t.me/stytam](https://t.me/stytam). Licença [MIT](../../LICENSE); componentes de terceiros: [THIRD_PARTY_NOTICES.md](../../THIRD_PARTY_NOTICES.md).
