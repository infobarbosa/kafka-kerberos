# Kafka com autenticacao SASL/GSSAPI 

Se ainda nao iniciou o laboratorio, esta eh a hora.
```
vagrant up
```

Nesse laboratorio eu jah deixei prontas as keytabs para:
- "aplicacao1" - aplicacao produtora, escreve em um topico "teste"  no Kafka
- "aplicacao2" - aplicacao consumidora, leh de um topico "teste" no Kafka
- "admin" - usuario que emularia um administrador do sistema. vamos utiliza-lo em outro laboratorio de autorizacao.
- "kafka" - usuario de sistema do Kafka.

Para conferir essas informacoes, voce deve se conectar na instancia do Kerberos
```
vagrant ssh kerberos

ls -latr keytabs
```

Perceba que hah tres keytabs para o kafka ("kafka1", "kafka1.service.keytab", "kafka2.service.keytab", "kafka3.service.keytab").

Eh necessario porque cada broker precisa de uma keytab distinta que inclui o hostname (ou DNS).

Para maior conforto, durante o provisionamento das instancias (vide linhas abaixo no Vagrantfile), fiz o download das respectivas keytabs.
```
mkdir -p /home/vagrant/keytabs/
scp -o "StrictHostKeyChecking no" vagrant@kerberos:/home/vagrant/keytabs/kafka1.service.keytab /home/vagrant/keytabs/
```

Esse acao simula um cenario onde voce, administrador, solicita ao time de AD as credenciais de sistema para suas maquinas de Kafka. Apos recebe-las voce as armazenaria em um diretorio hipotetico ~/keytabs.

Agora voce precisa se conectar em cada maquina Kafka e fazer o setup para que o Kafka reconheca essas credenciais.

## Etapa 1 - server.properties

Abra o arquivo server.properties e adicionar o endpoint para SASL
```
vi /etc/kafka/server.properties
```

Encontre o parametro "listeners" abaixo:
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

# Passo 2 - JAAS

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

# Etapa 3 - kafka.service

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

## Etapa 4 - Checagem

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

Agora vah ateh a maquina do Kerberos
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

## Etapa 5 - Aplicacao cliente