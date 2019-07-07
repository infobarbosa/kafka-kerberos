# Kafka com autenticacao SASL/GSSAPI 

Se ainda nao iniciou o laboratorio, esta eh a hora. 
```
vagrant up
```
Caso seja a primeira vez entao aproveite pra ir fazer um cafe, na minha maquina o provisionamento demora uns 20 a 30 minutos pra terminar.

Nesse laboratorio eu jah deixei prontas as keytabs para:
- **aplicacao1** - aplicacao produtora, escreve em um topico "teste"  no Kafka
- **aplicacao2** - aplicacao consumidora, leh de um topico "teste" no Kafka
- **admin** - usuario que emularia um administrador do sistema. vamos utiliza-lo em outro laboratorio de autorizacao.
- **kafka** - usuario de sistema do Kafka.

Para conferir essas informacoes, voce deve se conectar na instancia do Kerberos
```
vagrant ssh kerberos

ls -latr keytabs
```

Perceba que hah tres keytabs para o kafka ("kafka1.service.keytab", "kafka2.service.keytab", "kafka3.service.keytab").

Eh necessario porque cada broker precisa de uma keytab distinta que inclui o hostname (ou DNS).

Para maior conforto, durante o provisionamento das instancias (vide linhas abaixo no Vagrantfile), fiz o download das respectivas keytabs.
```
mkdir -p /home/vagrant/keytabs/
scp -o "StrictHostKeyChecking no" vagrant@kerberos:/home/vagrant/keytabs/kafka1.service.keytab /home/vagrant/keytabs/
```

Esse acao simula um cenario onde voce, administrador, solicita ao time de AD as credenciais de sistema para suas maquinas de Kafka. Apos recebe-las voce as armazenaria em um diretorio hipotetico ~/keytabs.

Agora voce precisa se conectar em cada maquina Kafka e fazer o setup para que o Kafka reconheca essas credenciais.

### Etapa 1 - server.properties

Abra o arquivo _server.properties_ e adicionar o endpoint para SASL
```
vi /etc/kafka/server.properties
```

Encontre o parametro _listeners_ abaixo:
```
listeners=PLAINTEXT://0.0.0.0:9092,SSL://0.0.0.0:9093
```

Acrescente um listener para SASL_SSL escutando a porta 9094
```
listeners=PLAINTEXT://0.0.0.0:9092,SSL://0.0.0.0:9093,SASL_SSL://0.0.0.0:9094
```

Agora encontre o parametro "advertised.listeners" e faca o mesmo:
```
advertised.listeners=PLAINTEXT://kafka1.infobarbosa.github.com:9092,SSL://kafka1.infobarbosa.github.com:9093
```

Altere para:
```
advertised.listeners=PLAINTEXT://kafka1.infobarbosa.github.com:9092,SSL://kafka1.infobarbosa.github.com:9093,SASL_SSL://kafka1.infobarbosa.github.com:9094
```

Faca o mesmo para os brokers 2 e 3 atentando-se em especial aas portas e aos nomes dos hosts que precisam corresponder exatamente ao DNS de cada um.

Agora inclua os dois parametros abaixo em qualquer lugar do arquivo (sugiro que seja apos "advertised.listeners"  pra ficar mais facil de debugar)
```
sasl.enabled.mechanisms=GSSAPI
sasl.kerberos.service.name=kafka
ssl.client.auth=required
```

GSSAPI indica o uso de Kerberos como mecanismo habilitado de autenticacao.

"kafka" eh o usuario de sistema criado e exportado automaticamente pelo vagrant durante o provisionamento.

### Etapa 2 - JAAS

Vamos agora configurar um arquivo "kafka_server_jaas.conf" com o seguinte conteudo:
```
KafkaServer {
    com.sun.security.auth.module.Krb5LoginModule required
    useKeyTab=true
    storeKey=true
    keyTab="/home/vagrant/keytabs/kafka.service.keytab"
    principal="kafka/<<KAFKA-SERVER-PUBLIC-DNS>>@KAFKA.INFOBARBOSA";
};
```

No lab eu considero que o arquivo foi criado debaixo do diretorio /home/vagrant. Obviamente em sua instituicao voce escolhera um diretorio mais adequado.

O broker 1, por exemplo, ficaria assim:
```
KafkaServer {
    com.sun.security.auth.module.Krb5LoginModule required
    useKeyTab=true
    storeKey=true
    keyTab="/home/vagrant/keytabs/kafka1.service.keytab"
    principal="kafka/kafka1.infobarbosa.github.com@KAFKA.INFOBARBOSA";
};
```

Faca o mesmo em todos os brokers atentando-se aos respectivos nomes de keytabs e DNS de cada host.

### Etapa 3 - kafka.service

Eh hora de ajustar o daemon _kafka.service_ para que o broker reconheca o arquivo "kafka_server_jaas.conf" durante a inicializacao.
```
sudo vi /etc/systemd/system/kafka.service
```

Voce deve acrescentar o parametro de JVM abaixo atraves da variavel de ambiente KAFKA_OPTS.
```
-Djava.security.auth.login.config=/home/vagrant/kafka_server_jaas.conf
```

O trecho que agora estah assim:
```
Environment="KAFKA_OPTS= \
	-Dcom.sun.management.jmxremote.authenticate=false \
	-Dcom.sun.management.jmxremote.ssl=false \
	-Djava.rmi.server.hostname=kafka1.infobarbosa.github.com \
	-Dcom.sun.management.jmxremote.port=9999 \
	-Dcom.sun.management.jmxremote.rmi.port=9999 \
	-Djava.net.preferIPv4Stack=true"
```

Ficarah assim:
```
Environment="KAFKA_OPTS= \
	-Dcom.sun.management.jmxremote.authenticate=false \
	-Dcom.sun.management.jmxremote.ssl=false \
	-Djava.rmi.server.hostname=kafka1.infobarbosa.github.com \
	-Dcom.sun.management.jmxremote.port=9999 \
	-Dcom.sun.management.jmxremote.rmi.port=9999 \
	-Djava.net.preferIPv4Stack=true \
    -Djava.security.auth.login.config=/home/vagrant/kafka_server_jaas.conf"
```

Apos isso voce deve recarregar o servico:
```
sudo systemctl daemon-reload
```

Tambem deve reiniciar o Kafka:
```
sudo systemctl restart kafka
```

Agora cheque se o servico esta executando normalmente:
```
sudo systemctl status kafka
```

### Etapa 4 - Checagem

Nos brokers kafka:
```
sudo systemctl status kafka
```

Status "Running" eh soh sucesso!

```
sudo journalctl -n 100 -u kafka

sudo grep EndPoint /var/log/kafka/server.log
```

Encontrando a porta 9094 LISTEN
```
netstat -na | grep 9094
```

Agora na maquina do Kerberos:
```
vagrant ssh kerberos
```

Verifique os logs de autenticacao dos brokers:
```
sudo cat /var/log/krb5kdc.log
```

Voce deve encontrar uma linha de log de autenticacao para cada broker mais ou menos parecido com isso:
```
Jul 07 17:57:09 kerberos.infobarbosa.github.com krb5kdc[5304](info): AS_REQ (4 etypes {18 17 16 23}) 192.168.56.13: ISSUE: authtime 1562522229, etypes {rep=18 tkt=18 ses=18}, kafka/kafka3.infobarbosa.github.com@KAFKA.INFOBARBOSA for krbtgt/KAFKA.INFOBARBOSA@KAFKA.INFOBARBOSA
```

### Etapa 5 - Aplicacao cliente

Essa eh a parte mais facil. Nao eh preciso fazer alteracoes em codigo. :)

Primeiro, abra dois terminais, um para a aplicacao1 e outro para a aplicacao2. Mantenha o terminal para o kerberos aberto tambem.

#### aplicacao1
No primeiro terminal para kafka-client, inicialize a aplicacao1 utilizando a classe 'SaslAuthenticationProducer':
```
vagrant ssh kafka-client

cd aplicacao1

java -cp target/aplicacao1-1.0-SNAPSHOT-jar-with-dependencies.jar com.github.infobarbosa.kafka.SaslAuthenticationProducer
```

Imediatamente a aplicacao devera iniciar a producao de mensagens no topico 'teste' no Kafka.
As mensagens devem aparecer no console mais ou menos como a seguir:
```
2019-07-07 20:52:17 - K: key 10. V: SASL authentication value 10 at ts 1562532737558. TS: 1562532737558
2019-07-07 20:52:17 - K: key 11. V: SASL authentication value 11 at ts 1562532737658. TS: 1562532737658
2019-07-07 20:52:17 - K: key 12. V: SASL authentication value 12 at ts 1562532737759. TS: 1562532737759
```

Tambem eh interessante olhar os logs. Imediatamente apos o inicio da execucao, as primeiras linhas terao uma indicacao do handshake da aplicacao com o Kerberos. Algo assim:
```
2019-07-07 20:52:15 - Set SASL client state to SEND_APIVERSIONS_REQUEST
2019-07-07 20:52:15 - Creating SaslClient: client=aplicacao1@KAFKA.INFOBARBOSA;service=kafka;serviceHostname=kafka1.infobarbosa.github.com;mechs=[GSSAPI]
2019-07-07 20:52:15 - Added sensor with name node--1.bytes-sent
2019-07-07 20:52:15 - Added sensor with name node--1.bytes-received
2019-07-07 20:52:15 - Added sensor with name node--1.latency
2019-07-07 20:52:15 - [Producer clientId=producer-tutorial] Created socket with SO_RCVBUF = 32768, SO_SNDBUF = 131072, SO_TIMEOUT = 0 to node -1
2019-07-07 20:52:16 - [Producer clientId=producer-tutorial] Completed connection to node -1. Fetching API versions.
2019-07-07 20:52:16 - SSL handshake completed successfully with peerHost 'kafka1.infobarbosa.github.com' peerPort 9094 peerPrincipal 'CN=kafka1.infobarbosa.github.com' cipherSuite 'TLS_DHE_DSS_WITH_AES_256_CBC_SHA256'
2019-07-07 20:52:16 - Set SASL client state to RECEIVE_APIVERSIONS_RESPONSE
2019-07-07 20:52:16 - Set SASL client state to SEND_HANDSHAKE_REQUEST
2019-07-07 20:52:16 - Set SASL client state to RECEIVE_HANDSHAKE_RESPONSE
2019-07-07 20:52:16 - Set SASL client state to INITIAL
2019-07-07 20:52:16 - Set SASL client state to INTERMEDIATE
2019-07-07 20:52:16 - Set SASL client state to CLIENT_COMPLETE
2019-07-07 20:52:16 - Set SASL client state to COMPLETE
2019-07-07 20:52:16 - [Producer clientId=producer-tutorial] Initiating API versions fetch from node -1.
2019-07-07 20:52:16 - [Producer clientId=producer-tutorial] Recorded API versions for node -1: (Produce(0): 0 to 7 [usable: 5], Fetch(1): 0 to 10 [usable: 7], ListOffsets(2): 0 to 5 [usable: 2], Metadata(3): 0 to 7 [usable: 5], LeaderAndIsr(4): 0 to 2 [usable: 1], StopReplica(5): 0 to 1 [usable: 0], UpdateMetadata(6): 0 to 5 [usable: 4], ControlledShutdown(7): 0 to 2 [usable: 1], OffsetCommit(8): 0 to 6 [usable: 3], OffsetFetch(9): 0 to 5 [usable: 3], FindCoordinator(10): 0 to 2 [usable: 1], JoinGroup(11): 0 to 4 [usable: 2], Heartbeat(12): 0 to 2 [usable: 1], LeaveGroup(13): 0 to 2 [usable: 1], SyncGroup(14): 0 to 2 [usable: 1], DescribeGroups(15): 0 to 2 [usable: 1], ListGroups(16): 0 to 2 [usable: 1], SaslHandshake(17): 0 to 1 [usable: 1], ApiVersions(18): 0 to 2 [usable: 1], CreateTopics(19): 0 to 3 [usable: 2], DeleteTopics(20): 0 to 3 [usable: 1], DeleteRecords(21): 0 to 1 [usable: 0], InitProducerId(22): 0 to 1 [usable: 0], OffsetForLeaderEpoch(23): 0 to 2 [usable: 0], AddPartitionsToTxn(24): 0 to 1 [usable: 0], AddOffsetsToTxn(25): 0 to 1 [usable: 0], EndTxn(26): 0 to 1 [usable: 0], WriteTxnMarkers(27): 0 [usable: 0], TxnOffsetCommit(28): 0 to 2 [usable: 0], DescribeAcls(29): 0 to 1 [usable: 0], CreateAcls(30): 0 to 1 [usable: 0], DeleteAcls(31): 0 to 1 [usable: 0], DescribeConfigs(32): 0 to 2 [usable: 1], AlterConfigs(33): 0 to 1 [usable: 0], AlterReplicaLogDirs(34): 0 to 1 [usable: 0], DescribeLogDirs(35): 0 to 1 [usable: 0], SaslAuthenticate(36): 0 to 1 [usable: 0], CreatePartitions(37): 0 to 1 [usable: 0], CreateDelegationToken(38): 0 to 1 [usable: 0], RenewDelegationToken(39): 0 to 1 [usable: 0], ExpireDelegationToken(40): 0 to 1 [usable: 0], DescribeDelegationToken(41): 0 to 1 [usable: 0], DeleteGroups(42): 0 to 1 [usable: 0], UNKNOWN(43): 0)
2019-07-07 20:52:16 - [Producer clientId=producer-tutorial] Sending metadata request (type=MetadataRequest, topics=teste) to node kafka1.infobarbosa.github.com:9094 (id: -1 rack: null)
2019-07-07 20:52:16 - Cluster ID: 1NkbeldpQxWCi9fM6cGHMg
2019-07-07 20:52:16 - Updated cluster metadata version 2 to Cluster(id = 1NkbeldpQxWCi9fM6cGHMg, nodes = [kafka3.infobarbosa.github.com:9094 (id: 3 rack: r1), kafka1.infobarbosa.github.com:9094 (id: 1 rack: r1), kafka2.infobarbosa.github.com:9094 (id: 2 rack: r1)], partitions = [Partition(topic = teste, partition = 2, leader = 1, replicas = [2,1,3], isr = [1,2,3], offlineReplicas = []), Partition(topic = teste, partition = 1, leader = 1, replicas = [1,3,2], isr = [1,2,3], offlineReplicas = []), Partition(topic = teste, partition = 4, leader = 1, replicas = [1,2,3], isr = [1,2,3], offlineReplicas = []), Partition(topic = teste, partition = 3, leader = 3, replicas = [3,1,2], isr = [1,2,3], offlineReplicas = []), Partition(topic = teste, partition = 0, leader = 3, replicas = [3,2,1], isr = [1,2,3], offlineReplicas = []), Partition(topic = teste, partition = 10, leader = 1, replicas = [1,2,3], isr = [1,2,3], offlineReplicas = []), Partition(topic = teste, partition = 9, leader = 3, replicas = [3,1,2], isr = [1,2,3], offlineReplicas = []), Partition(topic = teste, partition = 11, leader = 2, replicas = [2,3,1], isr = [1,2,3], offlineReplicas = []), Partition(topic = teste, partition = 6, leader = 3, replicas = [3,2,1], isr = [1,2,3], offlineReplicas = []), Partition(topic = teste, partition = 5, leader = 2, replicas = [2,3,1], isr = [1,2,3], offlineReplicas = []), Partition(topic = teste, partition = 8, leader = 1, replicas = [2,1,3], isr = [1,2,3], offlineReplicas = []), Partition(topic = teste, partition = 7, leader = 1, replicas = [1,3,2], isr = [1,2,3], offlineReplicas = [])])
2019-07-07 20:52:16 - [Producer clientId=producer-tutorial] Initiating connection to node kafka1.infobarbosa.github.com:9094 (id: 1 rack: r1)
2019-07-07 20:52:16 - Set SASL client state to SEND_APIVERSIONS_REQUEST
2019-07-07 20:52:16 - Creating SaslClient: client=aplicacao1@KAFKA.INFOBARBOSA;service=kafka;serviceHostname=kafka1.infobarbosa.github.com;mechs=[GSSAPI]
```

#### aplicacao2

Seguindo o mesmo principio, no segundo terminal para kafka-client, inicialize a aplicacao2:
```
vagrant ssh kafka-client

cd aplicacao2

java -cp target/aplicacao2-1.0-SNAPSHOT-jar-with-dependencies.jar com.github.infobarbosa.kafka.SaslAuthenticationConsumer
```

Da mesma forma serah possivel checar mensagens nos logs:
```
2019-07-07 21:04:58,277 INFO  [c.g.i.k.SaslAuthenticationConsumer] (main:) K: key 0; V: SASL authentication value 0 at ts 1562533496428; TS: 1562533497786
2019-07-07 21:04:58,384 INFO  [c.g.i.k.SaslAuthenticationConsumer] (main:) K: key 1; V: SASL authentication value 1 at ts 1562533497903; TS: 1562533497904
2019-07-07 21:04:58,489 INFO  [c.g.i.k.SaslAuthenticationConsumer] (main:) K: key 2; V: SASL authentication value 2 at ts 1562533498004; TS: 1562533498004
```

#### kerberos

Agora vamos checar os logs do Kerberos:
```
vagrant ssh kerberos

sudo cat /var/log/krb5kdc.log
```

Perceba as mensagens que apontam a autenticacao das aplicacoes clientes em cada broker:

**aplicacao1**
```
Jul 07 20:52:17 kerberos.infobarbosa.github.com krb5kdc[5304](info): TGS_REQ (4 etypes {18 17 16 23}) 192.168.56.14: ISSUE: authtime 1562532736, etypes {rep=18 tkt=18 ses=18}, aplicacao1@KAFKA.INFOBARBOSA for kafka/kafka1.infobarbosa.github.com@KAFKA.INFOBARBOSA
Jul 07 20:52:18 kerberos.infobarbosa.github.com krb5kdc[5304](info): TGS_REQ (4 etypes {18 17 16 23}) 192.168.56.14: ISSUE: authtime 1562532736, etypes {rep=18 tkt=18 ses=18}, aplicacao1@KAFKA.INFOBARBOSA for kafka/kafka3.infobarbosa.github.com@KAFKA.INFOBARBOSA
Jul 07 20:52:18 kerberos.infobarbosa.github.com krb5kdc[5304](info): TGS_REQ (4 etypes {18 17 16 23}) 192.168.56.14: ISSUE: authtime 1562532736, etypes {rep=18 tkt=18 ses=18}, aplicacao1@KAFKA.INFOBARBOSA for kafka/kafka2.infobarbosa.github.com@KAFKA.INFOBARBOSA

```

**aplicacao2**
```
Jul 07 21:04:42 kerberos.infobarbosa.github.com krb5kdc[5304](info): TGS_REQ (4 etypes {18 17 16 23}) 192.168.56.14: ISSUE: authtime 1562533481, etypes {rep=18 tkt=18 ses=18}, aplicacao2@KAFKA.INFOBARBOSA for kafka/kafka1.infobarbosa.github.com@KAFKA.INFOBARBOSA
Jul 07 21:04:42 kerberos.infobarbosa.github.com krb5kdc[5304](info): TGS_REQ (4 etypes {18 17 16 23}) 192.168.56.14: ISSUE: authtime 1562533481, etypes {rep=18 tkt=18 ses=18}, aplicacao2@KAFKA.INFOBARBOSA for kafka/kafka2.infobarbosa.github.com@KAFKA.INFOBARBOSA
Jul 07 21:04:42 kerberos.infobarbosa.github.com krb5kdc[5304](info): TGS_REQ (4 etypes {18 17 16 23}) 192.168.56.14: ISSUE: authtime 1562533481, etypes {rep=18 tkt=18 ses=18}, aplicacao2@KAFKA.INFOBARBOSA for kafka/kafka3.infobarbosa.github.com@KAFKA.INFOBARBOSA
```

#### Encriptacao

Vamos checar se a encriptacao continua funcionando.
No segundo terminal para kafka-cliente, encerre a aplicacao2.
```
[CTRL+c]

sudo -i

sudo tcpdump -v -XX  -i enp0s8 'port 9094' -w dump.txt -c 1000

cat dump.txt
```

Atenção! **enp0s8** é a interface de rede utilizada para host-only na minha máquina.
Se o comando nao funcionar entao verifique quais interfaces estao funcionando via **ifconfig** ou **tcpdump --list-interfaces**

Eh isso, pessoal! Autenticacao kerberos e encriptacao TLS funcionando. Espero que tenha funcionado pra voces tambem.

Ateh a proxima!

Barbosa