# Kafka SSL Encryption and Authentication Lab

Esta é a segunda parte de um laboratório que visa exercitar features de segurança do Kafka.
Na [primeira parte](https://github.com/infobarbosa/kafka-security-base-box/blob/master/instructions/kafka-ssl-encryption.md) trabalhamos a **encriptação** dos dados. Agora vamos exercitar a **encriptação** combinada com **autenticação** utilizando TSL (o antigo SSL), modelo também conhecido como 2-way Authentication.<br/>
No exercício a seguir estou assumindo que você executou a primeira parte. Caso contrário, comece por [aqui](https://github.com/infobarbosa/kafka-security-base-box).<br/>

## Let's go!

Se ainda não tiver subido o laboratório, essa é a hora:
```
vagrant up
```

## Aplicação Cliente

Conecte-se na máquina da aplicação cliente:
```
vagrant ssh kafka-client
```

Crie de um certificado para a aplicação cliente:
```
export CLIPASS=senha-insegura

keytool -genkey -keystore ~/ssl/kafka.client.keystore.jks -validity 365 -storepass $CLIPASS -keypass $CLIPASS  -dname "CN=kafka-client.infobarbosa.github.com" -alias kafka-client -storetype pkcs12

keytool -list -v -keystore ~/ssl/kafka.client.keystore.jks -storepass $CLIPASS
```

## Criação do request file

Crie o request file que será assinado pela CA
```
keytool -keystore ~/ssl/kafka.client.keystore.jks -certreq -file /vagrant/client-cert-sign-request -alias kafka-client -storepass $CLIPASS -keypass $CLIPASS

```
## Enviando para a CA

Vamos simular o envio da requisição para a CA. Assim como no último lab, basta copiar para o diretório /vagrant, diretório raiz no host, compartilhado pelas vms deste laboratório:
**Atenção!** Se você executou o comando anterior na íntegra então o arquivo já estará no diretório /vagrant.
```
cp ~/ssl/client-cert-sign-request /vagrant/
```

## Assinatura do certificado

Abra outra janela e entre na máquina da autoridade certificadora:
```
vagrant ssh ca
```

Assine o certificado utilizando a CA:

```
export SRVPASS=serversecret
openssl x509 -req -CA ~/ssl/ca-cert -CAkey ~/ssl/ca-key -in /vagrant/client-cert-sign-request -out /vagrant/client-cert-signed -days 365 -CAcreateserial -passin pass:$SRVPASS
```

> **Atenção** <br/>
> É possível que você tenha problemas de permissão ao aquivo */home/vagrant/ssl/ca-cert.srl*, algo como
```
...
/home/vagrant/ssl/ca-cert.srl: Permission denied
...
```
> Para resolver, basta executar o comando a seguir:
```
sudo chown vagrant:vagrant /home/vagrant/ssl/ca-cert.srl
```
> Pronto! É só executar novamente o comando de assinatura do certificado

## Kafka Cliente, setup da keystore

Se fechou a sessão com a aplicação cliente, vai precisar disto:
```
export CLIPASS=senha-insegura
```

Vamos checar o certificado assinado.
```
keytool -printcert -v -file /vagrant/client-cert-signed
```
Se o output tiver algo como...
```
Owner: CN=kafka-client.infobarbosa.github.com
Issuer: CN=Kafka-Security-CA
```
...então estamos no caminho certo.

Crie a relação de confiaça importanto a chave pública da CA para a Keystore:
```
keytool -keystore ~/ssl/kafka.client.keystore.jks -alias CARoot -import -file /vagrant/ca-cert -storepass $CLIPASS -keypass $CLIPASS -noprompt
```

Agora importe o certificado assinado para a keystore:
```
keytool -keystore ~/ssl/kafka.client.keystore.jks -import -file /vagrant/client-cert-signed -alias kafka-client -storepass $CLIPASS -keypass $CLIPASS -noprompt
```

> **Atenção!**<br/>
> Os dois comandos acima precisam ser executados nessa ordem ou você vai pegar o seguinte erro:
```
...
keytool error: java.lang.Exception: Failed to establish chain from reply
```

Verifique se está tudo lá:
```
keytool -list -v -keystore ~/ssl/kafka.client.keystore.jks -storepass $CLIPASS
```

O output será algo assim:
```
...
Your keystore contains 2 entries
...
Certificate[1]:
Owner: CN=kafka-client.infobarbosa.github.com
Issuer: CN=Kafka-Security-CA
...
Certificate[2]:
Owner: CN=Kafka-Security-CA
Issuer: CN=Kafka-Security-CA
...
```

Dê uma olhada no códido das aplicações cliente.
As classes produtora e consumidora são, respectivamente:
* /home/vagrant/kafka-producer/src/main/java/com/github/infobarbosa/kafka/SslAuthenticationProducer.java
* /home/vagrant/kafka-consumer/src/main/java/com/github/infobarbosa/kafka/SslAuthenticationConsumer.java

Perceba a presença das linhas abaixo:
```
properties.put("ssl.keystore.location", "/home/vagrant/ssl/kafka.client.keystore.jks");
properties.put("ssl.keystore.password", "senha-insegura");
properties.put("ssl.key.password", "senha-insegura");
```

> Obviamente essas e outras propriedades não devem ser hard coded. Como boa prática devem ser injetadas via arquivo de configuração ou variáveis de ambiente.


## Kafka Broker

É necessário fazer um pequeno ajuste no broker, habilitar a propriedade **ssl.client.auth**. <br/>
Entre na máquina do Kafka:
```
vagrant ssh kafka1
```
Abra o arquivo server.properties no seu editor de texto:
```
vi /etc/kafka/server.properties
```
Inclua a nova propriedade **ssl.client.auth**:
```
ssl.client.auth=required
```
Reinicie o Kafka:
```
sudo systemctl restart kafka
```
Verifique se o serviço subiu:
```
sudo systemctl status kafka
```

## Hora do teste!

#### Janela 1

Na máquina kafka-client
```
vagrant ssh kafka-client

cd ~/kafka-producer
```
Se ainda não tiver feito o build da aplicação, essa é a hora:
```
mvn clean package
```
Agora execute a aplicação produtora:
```
java -cp target/kafka-producer-1.0-SNAPSHOT-jar-with-dependencies.jar com.github.infobarbosa.kafka.SslAuthenticationProducer
```

#### Janela 2

Na máquina kafka-client
```
vagrant ssh kafka-client

cd ~/kafka-consumer
```
Se ainda não tiver feito o build da aplicação, essa é a hora:
```
mvn clean package
```
Agora execute a aplicação consumidora:
```
java -cp target/kafka-consumer-1.0-SNAPSHOT-jar-with-dependencies.jar com.github.infobarbosa.kafka.SslAuthenticationConsumer
```

#### Janela 3. tcpdump na porta do servico para "escutar" o conteudo trafegado.

Esse comando pode ser executado tanto na maquina do Kafka (kafka1) como na aplicacao cliente (kafka-client)
Atenção! **enp0s8** é a interface de rede utilizada para host-only na minha máquina.
Se o comando nao funcionar entao verifique quais interfaces estao funcionando via **ifconfig** ou **tcpdump --list-interfaces**
```
sudo tcpdump -v -XX  -i enp0s8
```
Caso queira enviar o log para um arquivo para analisar melhor:
```
sudo -i
tcpdump -v -XX  -i enp0s8 -w dump.txt -c 100
```

#### Janela 4

Lembra que habilitamos a propriedade "ssl.client.auth" com o valor "required" no Kafka? O impacto desse ajuste pode ser visto aqui.<br/>
A partir de agora as aplicações cliente que tinham apenas a encriptação via SSL não conseguirão mais se comunicar com o cluster Kafka.

```
vagrant ssh kafka-client
cd ~/kafka-producer
java -cp target/kafka-producer-1.0-SNAPSHOT-jar-with-dependencies.jar com.github.infobarbosa.kafka.SslProducer
```

O resultado será uma mensagem de erro contendo um trecho como esse:
```
org.apache.kafka.common.errors.SslAuthenticationException: SSL handshake failed
Caused by: javax.net.ssl.SSLProtocolException: Handshake message sequence violation, 2
```

#### Janela 5

O mesmo vale para a aplicação consumidora:
```
vagrant ssh kafka-client
cd ~/kafka-consumer
java -cp target/kafka-consumer-1.0-SNAPSHOT-jar-with-dependencies.jar com.github.infobarbosa.kafka.SslConsumer
```

## Parabéns!

Você chegou lá! Se tentou executar o laboratório mas não deu certo de primeira, não desanime! Comece novamente e repita até conseguir. ;)

## Último alerta (de novo!)

Perceba que deixamos o broker respondendo na porta 9092 (plaintext).<br/>
Fizemos isso por uma razão, nem todas as aplicações têm condições de "rolar" para a porta encriptada imediatamente.<br/>
Desta forma, estabelecemos um tempo de carência para que as aplicações possam fazê-lo de forma gradativa.<br/>
O importante é remover o porta 9092 do listener o mais cedo possível.
Quando tiver finalizado sua configuração de listener no broker será parecida com isso:
```
listeners=PLAINTEXT://0.0.0.0:9093
advertised.listeners=PLAINTEXT://kafka1.infobarbosa.github.com:9093
```

Se tiver dúvidas, me envie! Tentarei ajudar como puder.<br/>

Até o próxima!
