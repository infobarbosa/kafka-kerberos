# Kafka Security | Base Box

Este projeto tem como objetivo exercitar as features de segurança do [Kafka](https://kafka.apache.org/).<br/>
Normalmente são esperados três níveis de segurança: [**encriptação**](instructions/kafka-ssl-encryption.md), **autenticação** e **autorização**.<br/>
Uma instalação inicial do Kafka não habilita qualquer nível de segurança. Portanto, é de responsabilidade do administrador do sistema habilitar tais recursos.<br/>
Não é propósito deste laboratório substituir as documentações disponíveis sobre o tema. As mesmas são claras e objetivas e podem ser encontradas [aqui](https://kafka.apache.org/documentation/#security) e [aqui](https://docs.confluent.io/current/security.html).<br/>
Com o laboratório pretendo simular um cenário onde há basicamente três atores (ou times):

- Desenvolvedor: profissional responsável pelo desenvolvimento da aplicação cliente do Kafka;
- Administrador: profissional responsável pela administração do ambiente Kafka;
- Segurança: profissional responsável pelo manejo de credenciais e permissões de segurança da empresa.

Para tanto, o conteúdo do laboratório possui algumas imagens Linux que sobem via [Vagrant](https://github.com/infobarbosa/kafka-security-base-box/blob/master/Vagrantfile):
- Zookeeper;
- Kafka;
- Aplicação cliente: imagem Linux com duas aplicações cliente simples, uma produtora e outra consumidora;
- Kerberos.

By the way, se não estiver familiarizado com o Vagrant, sugiro começar por [aqui](https://www.vagrantup.com/intro/index.html).<br/>
No [primeiro artigo](instructions/kafka-ssl-encryption.md) pretendo inicialmente trabalhar a encriptação de dados via SSL.<br/>
Os demais artigos ainda serão escritos assim como a montagem dos laboratórios. Be patient! Em breve estarão disponíveis.

Para iniciar os boxes basta digitar:
```
vagrant up
```

A montagem dos boxes leva entre 15 e 30 minutos, a depender da máquina que você tem. Vá tomar um café!

## Encriptação

As instruções para encriptação utilizando este lab podem ser encontradas [aqui](instructions/kafka-ssl-encryption.md).

## Autenticação

Comming soon

## Autorização

Comming soon
