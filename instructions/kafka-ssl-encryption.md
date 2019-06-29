# Kafka SSL Encryption Lab

Esse laboratório tem por objetivo exercitar a feature de encriptação do Kafka. O conteúdo completo pode ser encontrado [aqui](https://github.com/infobarbosa/kafka-security-base-box).<br/>

## Let's go!

Vamos primeiramente testar as nossas aplicações clientes e constatar o quanto inseguro pode ser tráfego de dados quando o Kafka não habilita encriptação.<br/>


Se ainda não tiver subido o laboratório, essa é a hora:
```
vagrant up
```

## Primeiro teste (PLAINTEXT)

#### Janela 1
```
vagrant ssh kafka-client
cd ~/kafka-producer
mvn clean package
java -cp target/kafka-producer-1.0-SNAPSHOT-jar-with-dependencies.jar com.github.infobarbosa.kafka.PlaintextProducer
[Control + c]
```

#### Janela 2
```
vagrant ssh kafka-client
cd ~/kafka-consumer
mvn clean package
java -cp target/kafka-consumer-1.0-SNAPSHOT-jar-with-dependencies.jar com.github.infobarbosa.kafka.PlaintextConsumer
```

#### Janela 3

Executar um "tcpdump" para "escutar" o conteúdo trafegado.<br/>

Esse comando pode ser executado tanto na máquina do Kafka (kafka1) como na aplicação cliente (kafka-client)
```
vagrant ssh kafka-client
sudo tcpdump -v -XX  -i enp0s8 -c 10
sudo tcpdump -v -XX  -i enp0s8 -c 100 -w dump.txt
```
Agora que vimos o quanto expostos estão nossos dados, é hora de fazer o setup pra resolver isso com encriptação. <br/>

Abra outra janela e entre na máquina do Kafka:
```
vagrant ssh kafka1
```

### Primeiro uma limpeza...
Assim como eu, você pode querer executar esse lab muitas e muitas vezes então é legal manter o diretório raiz limpo.
```
rm /vagrant/cert-file
rm /vagrant/cert-signed
rm /vagrant/dump.txt
```

### Gerando o certificado e a keystore
```
export SRVPASS=senha-insegura
mkdir ssl
cd ssl

keytool -genkey -keystore kafka.server.keystore.jks -validity 365 -storepass $SRVPASS -keypass $SRVPASS  -dname "CN=kafka1.infobarbosa.github.com" -storetype pkcs12

ls -latrh
```
Vamos checar o conteúdo da keystore
```
keytool -list -v -keystore kafka.server.keystore.jks -storepass $SRVPASS
```

## Certification request

É hora de criar o certification request file. Esse arquivo vamos enviar para a **autoridade certificadora (CA)** pra que seja assinado.
```
keytool -keystore ~/ssl/kafka.server.keystore.jks -certreq -file /vagrant/cert-file -storepass $SRVPASS -keypass $SRVPASS
```

Pra simular o envio do arquivo para a CA, vamos copiar o arquivo **cert-file** para o diretorio **/vagrant** onde pode ser acessado pela CA.<br/>
**Observação:** O diretório **/vagrant** é, na verdade, o diretório raiz do projeto no host. Apenas foi compartilhado com a vm.
```
cp ~/ssl/cert-file /vagrant/
```

## Assinatura do certificado

Abra outro terminal e entre na máquina da autoridade certificadora:
```
vagrant ssh ca
```
Verifique se o arquivo está acessível:
```
ls -ltrh /vagrant/cert-file
```
Tudo pronto! É hora de efetivamente assinar o certificado:

```
export SRVPASS=senha-insegura

sudo openssl x509 -req -CA ~/ssl/ca-cert -CAkey ~/ssl/ca-key -in /vagrant/cert-file -out /vagrant/cert-signed -days 365 -CAcreateserial -passin pass:$SRVPASS
```
Veja que o comando **openssl** recebe cert-file (**-in /vagrant/cert-file**) e devolve cert-signed (**/vagrant/cert-signed**) no mesmo diretório /vagrant.<br/>
Por conveniência, gerei o output (**cert-signed**) direto no diretório /vagrant. Aqui estamos simulando a devolução do certificado assinado pelo time de segurança para o time de desenvolvimento, o que pode ser feito por email ou por algum processo formal na sua empresa.

## A chave pública da Autoridade Certificadora

Para este setup também vamos precisar da chave pública divulgada pela CA, então aproveita que está na máquina da CA e copie este certificado para o diretório /vagrant de forma a ficar acessível para as máquinas do Kafka (kafka1) e aplicação cliente (kafka-client).<br/>
```
cp ~/ssl/ca-cert /vagrant/
```

## O recebimento do certificado assinado

De volta à maquina do Kafka...
```
vagrant ssh kafka1
```
...vamos checar o certificado assinado.
```
keytool -printcert -v -file /vagrant/cert-signed
```
Se estiver no caminho certo, você vai encontrar as seguintes linhas no output:
```
Owner: CN=kafka1.infobarbosa.github.com
Issuer: CN=Kafka-Security-CA
```
## Criando a relação de confiança com a CA

Antes de seguir, talvez você queira checar a keystore pra ter a visão do antes e depois:
```
keytool -list -v -keystore /home/vagrant/ssl/kafka.server.keystore.jks -storepass $SRVPASS
```
Hora de executar as devidas importações.

### Trustore

Se tiver fechado o terminal, vai precisar disso:
```
export SRVPASS=senha-insegura
```

Cria a truststore e importa o certificado da CA (**ca-cert**) para ela:
```
keytool -keystore kafka.server.truststore.jks -alias CARoot -import -file /vagrant/ca-cert -storepass $SRVPASS -keypass $SRVPASS -noprompt
```
Checando se deu certo:
```
keytool -list -v -keystore /home/vagrant/ssl/kafka.server.truststore.jks -storepass $SRVPASS

```
### Keystore

Importando a CA e o certificado assinado para a keystore:
```
keytool -keystore kafka.server.keystore.jks -alias CARoot -import -file /vagrant/ca-cert -storepass $SRVPASS -keypass $SRVPASS -noprompt

keytool -keystore kafka.server.keystore.jks -import -file /vagrant/cert-signed -storepass $SRVPASS -keypass $SRVPASS -noprompt
```
Checando se deu certo:
```
keytool -list -v -keystore /home/vagrant/ssl/kafka.server.keystore.jks -storepass $SRVPASS
```
Se estamos no caminho certo, o output será algo como:
```
...
Alias name: mykey
...
Certificate[1]:
Owner: CN=kafka1.infobarbosa.github.com
Issuer: CN=Kafka-Security-CA
...
Certificate[2]:
Owner: CN=Kafka-Security-CA
Issuer: CN=Kafka-Security-CA
```
Seguido de:
```
Alias name: caroot
Owner: CN=Kafka-Security-CA
Issuer: CN=Kafka-Security-CA
```
## kafka

Perfeito! Agora vamos ajustar configurações no broker alterando o arquivo **/etc/kafka/server.properties**:
```
vi /etc/kafka/server.properties
```
Substitua as linhas...
```
listeners=PLAINTEXT://0.0.0.0:9092
advertised.listeners=PLAINTEXT://kafka1.infobarbosa.github.com:9092
```
...por:
```
listeners=PLAINTEXT://0.0.0.0:9092,SSL://0.0.0.0:9093
advertised.listeners=PLAINTEXT://kafka1.infobarbosa.github.com:9092,SSL://kafka1.infobarbosa.github.com:9093
```

Acrescente também as linhas abaixo:
```
ssl.keystore.location=/home/vagrant/ssl/kafka.server.keystore.jks
ssl.keystore.password=senha-insegura
ssl.key.password=senha-insegura
ssl.truststore.location=/home/vagrant/ssl/kafka.server.truststore.jks
ssl.truststore.password=senha-insegura

```
Reinicie o Kafka
```
sudo systemctl restart kafka
sudo systemctl status kafka  
```
Verifique se ele começou a responder na porta 9093:
```
sudo grep "EndPoint" /var/log/kafka/server.log
```

Uma outra verificação legal é testar o SSL de uma outra máquina:
```
openssl s_client -connect 192.168.56.12:9093
```
Se aparecer CONNECTED, parabéns!

## Aplicação cliente

Estamos quase lá! Vamos fazer a configuracao SSL na aplicacao cliente.
```
vagrant ssh kafka-client
export CLIPASS=clientpass
cd ~
mkdir ssl
cd ssl
```
Perceba acima que vamos utilizar uma chave para a truststore que é diferente da que usamos no servidor.<br/>
Algumas pessoas esperam que seja a mesma do servidor, mas não é necessário.

### Truststore

Gera a truststore importando a chave publica da autoridade certificadora (CA):

```
keytool -keystore kafka.client.truststore.jks -alias CARoot -import -file /vagrant/ca-cert  -storepass $CLIPASS -keypass $CLIPASS -noprompt
```
Agora veja se a importação está OK:
```
keytool -list -v -keystore kafka.client.truststore.jks -storepass $CLIPASS
```

A título de curiosidade, abra as classes SslProducer.java e SslConsumer.java, ambas debaixo de src/main/java/com/github/infobarbosa/kafka, e observe o uso das propriedades abaixo:
```
BOOTSTRAP_SERVERS_CONFIG=kafka1.infobarbosa.github.com:9093
security.protocol=SSL
ssl.truststore.location=/home/vagrant/ssl/kafka.client.truststore.jks
ssl.truststore.password=clientpass
```
Obviamente essas e outras propriedades não devem ser hard coded. Como boa prática devem ser injetadas via arquivo de configuração ou variáveis de ambiente.

## Segundo teste (SSL)

Vamos refazer o teste. Desta vez, utilizando as classes produtora e consumidora que já apontam para o kafka1 na porta 9093
#### Janela 1
```
cd ~/kafka-producer
java -cp target/kafka-producer-1.0-SNAPSHOT-jar-with-dependencies.jar com.github.infobarbosa.kafka.SslProducer
```

#### Janela 2
```
cd ~/kafka-consumer
java -cp target/kafka-consumer-1.0-SNAPSHOT-jar-with-dependencies.jar com.github.infobarbosa.kafka.SslConsumer
```

#### Janela 3. Opcional. tcpdump na porta do servico para "escutar" o conteudo trafegado.

Esse comando pode ser executado tanto na maquina do Kafka (kafka1) como na aplicacao cliente (kafka-client)
Atenção! **enp0s8** é a interface de rede utilizada para host-only na minha máquina.
Se o comando nao funcionar entao verifique quais interfaces estao funcionando via **ifconfig** ou **tcpdump --list-interfaces**
```
sudo tcpdump -v -XX  -i enp0s8
```
Caso queira enviar o log para um arquivo para analisar melhor:
```
sudo -i
sudo tcpdump -v -XX  -i enp0s8 -w dump.txt -c 100
```

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
Até o próximo artigo!
