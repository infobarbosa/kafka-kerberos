# -*- mode: ruby -*-
# vi: set ft=ruby :

Vagrant.configure("2") do |config|
  #configuracao da instancia da autoridade certificadora (CA)
  config.vm.define "ca" do |ca|
    ca.vm.box = "ubuntu/xenial64"

    ca.vm.network "private_network", ip: "192.168.56.16"
    ca.vm.hostname = "ca.infobarbosa.github.com"
    ca.vm.provider "virtualbox" do |v|
      v.memory = 512
      v.cpus = 1
      v.name = "ca-lab-security.vagrant"
    end

    ca.vm.provision "file", source: "config-files/hosts", destination: "hosts"

    ca.vm.provision "shell", inline: <<-SHELL
      #setup para passwordless
      mkdir -p /home/vagrant/.ssh
      cp /vagrant/config-files/id_rsa /home/vagrant/.ssh/
      chmod 600 /home/vagrant/.ssh/id_rsa
      cp /vagrant/config-files/id_rsa.pub /home/vagrant/.ssh/
      cat /vagrant/config-files/id_rsa.pub >> /home/vagrant/.ssh/authorized_keys
      sudo chown vagrant:vagrant /home/vagrant/.ssh/id_rsa
      sudo chown vagrant:vagrant /home/vagrant/.ssh/id_rsa.pub

      sudo cat hosts >> /etc/hosts

      #Gerando uma autoridade certificadora (CA)
      mkdir ssl
      openssl req -new -newkey rsa:4096 -days 365 -x509 -subj "/CN=Kafka-Security-CA" -keyout ssl/ca-key -out ssl/ca-cert -nodes
      sudo chown -R vagrant:vagrant ssl

    SHELL
  end

  #configuracao da instancia do kerberos
  config.vm.define "kerberos" do |kerberos|
    kerberos.vm.box = "centos/7"
    # Shared folder setting
    kerberos.vm.synced_folder ".", "/vagrant", type: 'virtualbox'

    kerberos.vm.network "private_network", ip: "192.168.56.15"
    kerberos.vm.hostname = "kerberos.infobarbosa.github.com"
    kerberos.vm.provider "virtualbox" do |v|
      v.memory = 512
      v.cpus = 1
      v.name = "kerberos-lab-security.vagrant"
    end

    kerberos.vm.provision "file", source: "config-files/kdc.conf", destination: "kdc.conf"
    kerberos.vm.provision "file", source: "config-files/kadm5.acl", destination: "kadm5.acl"
    kerberos.vm.provision "file", source: "config-files/krb5.conf", destination: "krb5.conf"
    kerberos.vm.provision "file", source: "config-files/hosts", destination: "hosts"

    kerberos.vm.provision "shell", inline: <<-SHELL
      #setup para passwordless
      mkdir -p /home/vagrant/.ssh
      cp /vagrant/config-files/id_rsa /home/vagrant/.ssh/
      chmod 600 /home/vagrant/.ssh/id_rsa
      cp /vagrant/config-files/id_rsa.pub /home/vagrant/.ssh/
      cat /vagrant/config-files/id_rsa.pub >> /home/vagrant/.ssh/authorized_keys
      sudo chown vagrant:vagrant /home/vagrant/.ssh/id_rsa
      sudo chown vagrant:vagrant /home/vagrant/.ssh/id_rsa.pub

      #hosts
      sudo cat hosts >> /etc/hosts

      #setup kerberos
      sudo yum install -y krb5-server
      sudo systemctl restart firewalld
      sudo firewall-cmd --permanent --add-port=88/tcp
      sudo firewall-cmd --permanent --add-port=88/udp
      sudo firewall-cmd --reload

      sudo chown root:root kdc.conf
      sudo chown root:root kadm5.acl
      sudo chown root:root krb5.conf
      sudo chmod 644 kdc.conf
      sudo chmod 644 kadm5.acl
      sudo chmod 644 krb5.conf
      sudo cp kdc.conf /var/kerberos/krb5kdc/
      sudo cp kadm5.acl /var/kerberos/krb5kdc/
      sudo cp krb5.conf /etc/

      export REALM="KAFKA.INFOBARBOSA"
      export ADMINPW="senha-insegura"

      sudo /usr/sbin/kdb5_util create -s -r KAFKA.INFOBARBOSA -P senha-insegura
      sudo kadmin.local -q "add_principal -pw senha-insegura admin/admin"

      sudo systemctl restart krb5kdc
      sudo systemctl restart kadmin

      ## O trecho abaixo nao faz parte do setup do Kerberos
      ## Trata-se da criacao das principals/credenciais para o laboratorio
      sudo kadmin.local -q "add_principal -randkey aplicacao1@KAFKA.INFOBARBOSA"
      sudo kadmin.local -q "add_principal -randkey aplicacao2@KAFKA.INFOBARBOSA"
      sudo kadmin.local -q "add_principal -randkey admin@KAFKA.INFOBARBOSA"

      sudo kadmin.local -q "add_principal -randkey kafka/kafka1.infobarbosa.github.com@KAFKA.INFOBARBOSA"
      sudo kadmin.local -q "add_principal -randkey kafka/kafka2.infobarbosa.github.com@KAFKA.INFOBARBOSA"
      sudo kadmin.local -q "add_principal -randkey kafka/kafka3.infobarbosa.github.com@KAFKA.INFOBARBOSA"

      ## criando as keytabs
      mkdir -p keytabs
      sudo kadmin.local -q "xst -kt keytabs/aplicacao1.user.keytab aplicacao1@KAFKA.INFOBARBOSA"
      sudo kadmin.local -q "xst -kt keytabs/aplicacao2.user.keytab aplicacao2@KAFKA.INFOBARBOSA"
      sudo kadmin.local -q "xst -kt keytabs/admin.user.keytab admin@KAFKA.INFOBARBOSA"
      sudo kadmin.local -q "xst -kt keytabs/kafka1.service.keytab kafka/kafka1.infobarbosa.github.com@KAFKA.INFOBARBOSA"
      sudo kadmin.local -q "xst -kt keytabs/kafka2.service.keytab kafka/kafka2.infobarbosa.github.com@KAFKA.INFOBARBOSA"
      sudo kadmin.local -q "xst -kt keytabs/kafka3.service.keytab kafka/kafka3.infobarbosa.github.com@KAFKA.INFOBARBOSA"
      sudo chown -R vagrant:vagrant keytabs

    SHELL
  end

  #configuracao da instancia do zookeeper
  config.vm.define "zookeeper1" do |zookeeper1|
    zookeeper1.vm.box = "ubuntu/xenial64"

    zookeeper1.vm.network "private_network", ip: "192.168.56.10"
    zookeeper1.vm.hostname = "zookeeper1.infobarbosa.github.com"
    zookeeper1.vm.provider "virtualbox" do |v|
      v.memory = 1024
      v.cpus = 1
      v.name = "zookeeper-lab-security.vagrant"
    end

    zookeeper1.vm.provision "file", source: "daemons/zookeeper.service", destination: "zookeeper.service"
    zookeeper1.vm.provision "file", source: "config-files/hosts", destination: "hosts"

    zookeeper1.vm.provision "shell", inline: <<-SHELL
      #setup para passwordless
      mkdir -p /home/vagrant/.ssh
      cp /vagrant/config-files/id_rsa /home/vagrant/.ssh/
      chmod 600 /home/vagrant/.ssh/id_rsa
      cp /vagrant/config-files/id_rsa.pub /home/vagrant/.ssh/
      cat /vagrant/config-files/id_rsa.pub >> /home/vagrant/.ssh/authorized_keys
      sudo chown vagrant:vagrant /home/vagrant/.ssh/id_rsa
      sudo chown vagrant:vagrant /home/vagrant/.ssh/id_rsa.pub

      sudo cat hosts >> /etc/hosts

      sudo add-apt-repository ppa:openjdk-r/ppa
      sudo apt-get -y update && sudo apt-get install -y openjdk-8-jdk
      wget -qO - https://packages.confluent.io/deb/5.2/archive.key | sudo apt-key add -
      sudo add-apt-repository "deb [arch=amd64] https://packages.confluent.io/deb/5.2 stable main"
      sudo apt-get -y update && sudo apt-get install -y confluent-community-2.12

      sudo ufw allow 2181/tcp

      sudo cp zookeeper.service /etc/systemd/system/zookeeper.service
      chown root:root /etc/systemd/system/zookeeper.service
      sudo systemctl enable zookeeper
      sudo systemctl start zookeeper

    SHELL
  end

  #configuracao da instancia do kafka
  config.vm.define "kafka1" do |kafka1|
    kafka1.vm.box = "ubuntu/xenial64"

    kafka1.vm.network "private_network", ip: "192.168.56.11"
    kafka1.vm.hostname = "kafka1.infobarbosa.github.com"
    kafka1.vm.provider "virtualbox" do |v|
      v.memory = 1536
      v.cpus = 1
      v.name = "kafka1-lab-security.vagrant"
    end

    kafka1.vm.provision "file", source: "daemons/kafka.service", destination: "kafka.service"
    kafka1.vm.provision "file", source: "config-files/server1.properties", destination: "server.properties"
    kafka1.vm.provision "file", source: "config-files/hosts", destination: "hosts"
    kafka1.vm.provision "file", source: "config-files/krb5.conf", destination: "krb5.conf"

    #root script
    $script1 = <<-SCRIPT
      echo "usuario shell: `whoami`}"

      cat hosts >> /etc/hosts

      add-apt-repository ppa:openjdk-r/ppa
      apt-get -y update && apt-get install -y openjdk-8-jdk
      wget -qO - https://packages.confluent.io/deb/5.2/archive.key | apt-key add -
      add-apt-repository "deb [arch=amd64] https://packages.confluent.io/deb/5.2 stable main"
      apt-get -y update && sudo apt-get install -y confluent-community-2.12

      ufw allow 9092/tcp
      ufw allow 9093/tcp
      ufw allow 9094/tcp
      ufw allow 9999/tcp

      mv server.properties /etc/kafka/server.properties

      #instalacao do client Kerberos
      bash -c "export DEBIAN_FRONTEND=noninteractive ; apt-get install -y krb5-user"
      bash -c "cat krb5.conf > /etc/krb5.conf"

    SCRIPT

    kafka1.vm.provision "shell", inline: $script1

    #normal user script
    $script2 = <<-SCRIPT
      echo "usuario shell: `whoami`}"

      #setup para passwordless
      mkdir -p /home/vagrant/.ssh
      cp /vagrant/config-files/id_rsa /home/vagrant/.ssh/
      chmod 600 /home/vagrant/.ssh/id_rsa
      cp /vagrant/config-files/id_rsa.pub /home/vagrant/.ssh/
      cat /vagrant/config-files/id_rsa.pub >> /home/vagrant/.ssh/authorized_keys
      chown vagrant:vagrant /home/vagrant/.ssh/id_rsa
      chown vagrant:vagrant /home/vagrant/.ssh/id_rsa.pub

      #setup da encriptacao SSL
      mkdir -p ssl
      echo "geracao da key e keystore"
      export SRVPASS=senha-insegura; keytool -genkey -keystore ssl/kafka.server.keystore.jks -validity 365 -storepass $SRVPASS -keypass $SRVPASS  -dname "CN=kafka1.infobarbosa.github.com" -storetype pkcs12
      echo "geracao do request para assinatura da chave pela autoridade certificadora"
      export SRVPASS=senha-insegura; keytool -keystore ssl/kafka.server.keystore.jks -certreq -file ssl/kafka1-cert-file -storepass $SRVPASS -keypass $SRVPASS
      chown -R vagrant:vagrant ssl
      echo "envio do request para assinatura"
      scp -o "StrictHostKeyChecking no" ssl/kafka1-cert-file vagrant@ca:/home/vagrant/ssl
      echo "assinatura da chave"
      ssh -o "StrictHostKeyChecking no" vagrant@ca "export SRVPASS=senha-insegura ; openssl x509 -req -CA ssl/ca-cert -CAkey ssl/ca-key -in ssl/kafka1-cert-file -out ssl/kafka1-cert-signed -days 365 -CAcreateserial -passin pass:$SRVPASS"
      echo "mundando o owner para vagrant"
      ssh -o "StrictHostKeyChecking no" vagrant@ca "chown -R vagrant:vagrant ssl"
      echo "copia da chave publica da autoridade certificadora"
      scp -o "StrictHostKeyChecking no" vagrant@ca:/home/vagrant/ssl/ca-cert /home/vagrant/ssl
      echo "copia a chave assinada"
      scp -o "StrictHostKeyChecking no" vagrant@ca:/home/vagrant/ssl/kafka1-cert-signed /home/vagrant/ssl
      echo "criacao da truststore importando o certificado"
      export SRVPASS=senha-insegura ; keytool -keystore /home/vagrant/ssl/kafka.server.truststore.jks -alias CARoot -import -file /home/vagrant/ssl/ca-cert -storepass $SRVPASS -keypass $SRVPASS -noprompt
      echo "importa o certificado para a keystore"
      export SRVPASS=senha-insegura ; keytool -keystore /home/vagrant/ssl/kafka.server.keystore.jks -alias CARoot -import -file /home/vagrant/ssl/ca-cert -storepass $SRVPASS -keypass $SRVPASS -noprompt
      echo "importa o certificado para a keystore"
      export SRVPASS=senha-insegura ; keytool -keystore /home/vagrant/ssl/kafka.server.keystore.jks -import -file /home/vagrant/ssl/kafka1-cert-signed -storepass $SRVPASS -keypass $SRVPASS -noprompt

      mkdir -p /home/vagrant/keytabs/
      scp -o "StrictHostKeyChecking no" vagrant@kerberos:/home/vagrant/keytabs/kafka1.service.keytab /home/vagrant/keytabs/

    SCRIPT

    kafka1.vm.provision "shell", inline: $script2, privileged: false

    #root finalizacao com start do Kafka
    $script3 = <<-SCRIPT
      echo "usuario shell: `whoami`}"

      cp kafka.service /etc/systemd/system/kafka.service
      chown root:root /etc/systemd/system/kafka.service
      systemctl enable kafka
      systemctl start kafka

    SCRIPT

    kafka1.vm.provision "shell", inline: $script3
  end

  #configuracao da instancia do kafka
  config.vm.define "kafka2" do |kafka2|
    kafka2.vm.box = "ubuntu/xenial64"

    kafka2.vm.network "private_network", ip: "192.168.56.12"
    kafka2.vm.hostname = "kafka2.infobarbosa.github.com"
    kafka2.vm.provider "virtualbox" do |v|
      v.memory = 1536
      v.cpus = 1
      v.name = "kafka2-lab-security.vagrant"
    end

    kafka2.vm.provision "file", source: "daemons/kafka.service", destination: "kafka.service"
    kafka2.vm.provision "file", source: "config-files/server2.properties", destination: "server.properties"
    kafka2.vm.provision "file", source: "config-files/hosts", destination: "hosts"
    kafka2.vm.provision "file", source: "config-files/krb5.conf", destination: "krb5.conf"

    #root script
    $script1 = <<-SCRIPT
      echo "usuario shell: `whoami`}"

      cat hosts >> /etc/hosts

      add-apt-repository ppa:openjdk-r/ppa
      apt-get -y update && apt-get install -y openjdk-8-jdk
      wget -qO - https://packages.confluent.io/deb/5.2/archive.key | apt-key add -
      add-apt-repository "deb [arch=amd64] https://packages.confluent.io/deb/5.2 stable main"
      apt-get -y update && sudo apt-get install -y confluent-community-2.12

      ufw allow 9092/tcp
      ufw allow 9093/tcp
      ufw allow 9094/tcp
      ufw allow 9999/tcp

      mv server.properties /etc/kafka/server.properties

      #instalacao do client Kerberos
      bash -c "export DEBIAN_FRONTEND=noninteractive ; apt-get install -y krb5-user"
      bash -c "cat krb5.conf > /etc/krb5.conf"

    SCRIPT

    kafka2.vm.provision "shell", inline: $script1

    #normal user script
    $script2 = <<-SCRIPT
      echo "usuario shell: `whoami`}"

      #setup para passwordless
      mkdir -p /home/vagrant/.ssh
      cp /vagrant/config-files/id_rsa /home/vagrant/.ssh/
      chmod 600 /home/vagrant/.ssh/id_rsa
      cp /vagrant/config-files/id_rsa.pub /home/vagrant/.ssh/
      cat /vagrant/config-files/id_rsa.pub >> /home/vagrant/.ssh/authorized_keys
      chown vagrant:vagrant /home/vagrant/.ssh/id_rsa
      chown vagrant:vagrant /home/vagrant/.ssh/id_rsa.pub

      #setup da encriptacao SSL
      mkdir -p ssl
      echo "geracao da key e keystore"
      export SRVPASS=senha-insegura; keytool -genkey -keystore ssl/kafka.server.keystore.jks -validity 365 -storepass $SRVPASS -keypass $SRVPASS  -dname "CN=kafka2.infobarbosa.github.com" -storetype pkcs12
      echo "geracao do request para assinatura da chave pela autoridade certificadora"
      export SRVPASS=senha-insegura; keytool -keystore ssl/kafka.server.keystore.jks -certreq -file ssl/kafka2-cert-file -storepass $SRVPASS -keypass $SRVPASS
      chown -R vagrant:vagrant ssl
      echo "envio do request para assinatura"
      scp -o "StrictHostKeyChecking no" ssl/kafka2-cert-file vagrant@ca:/home/vagrant/ssl
      echo "assinatura da chave"
      ssh -o "StrictHostKeyChecking no" vagrant@ca "export SRVPASS=senha-insegura ; openssl x509 -req -CA ssl/ca-cert -CAkey ssl/ca-key -in ssl/kafka2-cert-file -out ssl/kafka2-cert-signed -days 365 -CAcreateserial -passin pass:$SRVPASS"
      echo "mundando o owner para vagrant"
      ssh -o "StrictHostKeyChecking no" vagrant@ca "chown -R vagrant:vagrant ssl"
      echo "copia da chave publica da autoridade certificadora"
      scp -o "StrictHostKeyChecking no" vagrant@ca:/home/vagrant/ssl/ca-cert /home/vagrant/ssl
      echo "copia a chave assinada"
      scp -o "StrictHostKeyChecking no" vagrant@ca:/home/vagrant/ssl/kafka2-cert-signed /home/vagrant/ssl
      echo "criacao da truststore importando o certificado"
      export SRVPASS=senha-insegura ; keytool -keystore /home/vagrant/ssl/kafka.server.truststore.jks -alias CARoot -import -file /home/vagrant/ssl/ca-cert -storepass $SRVPASS -keypass $SRVPASS -noprompt
      echo "importa o certificado para a keystore"
      export SRVPASS=senha-insegura ; keytool -keystore /home/vagrant/ssl/kafka.server.keystore.jks -alias CARoot -import -file /home/vagrant/ssl/ca-cert -storepass $SRVPASS -keypass $SRVPASS -noprompt
      echo "importa o certificado para a keystore"
      export SRVPASS=senha-insegura ; keytool -keystore /home/vagrant/ssl/kafka.server.keystore.jks -import -file /home/vagrant/ssl/kafka2-cert-signed -storepass $SRVPASS -keypass $SRVPASS -noprompt

      mkdir -p /home/vagrant/keytabs/
      scp -o "StrictHostKeyChecking no" vagrant@kerberos:/home/vagrant/keytabs/kafka2.service.keytab /home/vagrant/keytabs/

    SCRIPT

    kafka2.vm.provision "shell", inline: $script2, privileged: false

    #root finalizacao com start do Kafka
    $script3 = <<-SCRIPT
      echo "usuario shell: `whoami`}"

      cp kafka.service /etc/systemd/system/kafka.service
      chown root:root /etc/systemd/system/kafka.service
      systemctl enable kafka
      systemctl start kafka

    SCRIPT

    kafka2.vm.provision "shell", inline: $script3

  end

  #configuracao da instancia do kafka
  config.vm.define "kafka3" do |kafka3|
    kafka3.vm.box = "ubuntu/xenial64"

    kafka3.vm.network "private_network", ip: "192.168.56.13"
    kafka3.vm.hostname = "kafka3.infobarbosa.github.com"
    kafka3.vm.provider "virtualbox" do |v|
      v.memory = 1536
      v.cpus = 1
      v.name = "kafka3-lab-security.vagrant"
    end

    kafka3.vm.provision "file", source: "daemons/kafka.service", destination: "kafka.service"
    kafka3.vm.provision "file", source: "config-files/server3.properties", destination: "server.properties"
    kafka3.vm.provision "file", source: "config-files/hosts", destination: "hosts"
    kafka3.vm.provision "file", source: "config-files/krb5.conf", destination: "krb5.conf"

    #root script
    $script1 = <<-SCRIPT
      echo "usuario shell: `whoami`}"

      cat hosts >> /etc/hosts

      add-apt-repository ppa:openjdk-r/ppa
      apt-get -y update && apt-get install -y openjdk-8-jdk
      wget -qO - https://packages.confluent.io/deb/5.2/archive.key | apt-key add -
      add-apt-repository "deb [arch=amd64] https://packages.confluent.io/deb/5.2 stable main"
      apt-get -y update && sudo apt-get install -y confluent-community-2.12

      ufw allow 9092/tcp
      ufw allow 9093/tcp
      ufw allow 9094/tcp
      ufw allow 9999/tcp

      mv server.properties /etc/kafka/server.properties

      #instalacao do client Kerberos
      bash -c "export DEBIAN_FRONTEND=noninteractive ; apt-get install -y krb5-user"
      bash -c "cat krb5.conf > /etc/krb5.conf"

    SCRIPT

    kafka3.vm.provision "shell", inline: $script1

    #normal user script
    $script2 = <<-SCRIPT
      echo "usuario shell: `whoami`}"

      #setup para passwordless
      mkdir -p /home/vagrant/.ssh
      cp /vagrant/config-files/id_rsa /home/vagrant/.ssh/
      chmod 600 /home/vagrant/.ssh/id_rsa
      cp /vagrant/config-files/id_rsa.pub /home/vagrant/.ssh/
      cat /vagrant/config-files/id_rsa.pub >> /home/vagrant/.ssh/authorized_keys
      chown vagrant:vagrant /home/vagrant/.ssh/id_rsa
      chown vagrant:vagrant /home/vagrant/.ssh/id_rsa.pub

      #setup da encriptacao SSL
      mkdir -p ssl
      echo "geracao da key e keystore"
      export SRVPASS=senha-insegura; keytool -genkey -keystore ssl/kafka.server.keystore.jks -validity 365 -storepass $SRVPASS -keypass $SRVPASS  -dname "CN=kafka3.infobarbosa.github.com" -storetype pkcs12
      echo "geracao do request para assinatura da chave pela autoridade certificadora"
      export SRVPASS=senha-insegura; keytool -keystore ssl/kafka.server.keystore.jks -certreq -file ssl/kafka3-cert-file -storepass $SRVPASS -keypass $SRVPASS
      chown -R vagrant:vagrant ssl
      echo "envio do request para assinatura"
      scp -o "StrictHostKeyChecking no" ssl/kafka3-cert-file vagrant@ca:/home/vagrant/ssl
      echo "assinatura da chave"
      ssh -o "StrictHostKeyChecking no" vagrant@ca "export SRVPASS=senha-insegura ; openssl x509 -req -CA ssl/ca-cert -CAkey ssl/ca-key -in ssl/kafka3-cert-file -out ssl/kafka3-cert-signed -days 365 -CAcreateserial -passin pass:$SRVPASS"
      echo "mundando o owner para vagrant"
      ssh -o "StrictHostKeyChecking no" vagrant@ca "chown -R vagrant:vagrant ssl"
      echo "copia da chave publica da autoridade certificadora"
      scp -o "StrictHostKeyChecking no" vagrant@ca:/home/vagrant/ssl/ca-cert /home/vagrant/ssl
      echo "copia a chave assinada"
      scp -o "StrictHostKeyChecking no" vagrant@ca:/home/vagrant/ssl/kafka3-cert-signed /home/vagrant/ssl
      echo "criacao da truststore importando o certificado"
      export SRVPASS=senha-insegura ; keytool -keystore /home/vagrant/ssl/kafka.server.truststore.jks -alias CARoot -import -file /home/vagrant/ssl/ca-cert -storepass $SRVPASS -keypass $SRVPASS -noprompt
      echo "importa o certificado para a keystore"
      export SRVPASS=senha-insegura ; keytool -keystore /home/vagrant/ssl/kafka.server.keystore.jks -alias CARoot -import -file /home/vagrant/ssl/ca-cert -storepass $SRVPASS -keypass $SRVPASS -noprompt
      echo "importa o certificado para a keystore"
      export SRVPASS=senha-insegura ; keytool -keystore /home/vagrant/ssl/kafka.server.keystore.jks -import -file /home/vagrant/ssl/kafka3-cert-signed -storepass $SRVPASS -keypass $SRVPASS -noprompt

      mkdir -p /home/vagrant/keytabs/
      scp -o "StrictHostKeyChecking no" vagrant@kerberos:/home/vagrant/keytabs/kafka3.service.keytab /home/vagrant/keytabs/

    SCRIPT

    kafka3.vm.provision "shell", inline: $script2, privileged: false

    #root finalizacao com start do Kafka
    $script3 = <<-SCRIPT
      echo "usuario shell: `whoami`}"

      cp kafka.service /etc/systemd/system/kafka.service
      chown root:root /etc/systemd/system/kafka.service
      systemctl enable kafka
      systemctl start kafka

    SCRIPT

    kafka3.vm.provision "shell", inline: $script3

  end


  #configuracao da instancia da aplicacao cliente
  config.vm.define "kafka-client" do |client|
    client.vm.box = "ubuntu/xenial64"

    client.vm.network "private_network", ip: "192.168.56.14"
    client.vm.hostname = "kafka-client.infobarbosa.github.com"
    client.vm.provider "virtualbox" do |v|
      v.memory = 512
      v.cpus = 1
      v.name = "kafka-client-lab-security.vagrant"
    end

    client.vm.provision "file", source: "config-files/hosts", destination: "hosts"
    client.vm.provision "file", source: "config-files/krb5.conf", destination: "krb5.conf"

    #root script
    $script1 = <<-SCRIPT
      echo "usuario shell: `whoami`}"

      cat hosts >> /etc/hosts

      add-apt-repository ppa:openjdk-r/ppa
      apt-get -y update && apt-get install -y openjdk-8-jdk
      apt-get install -y maven

      #instalacao do client Kerberos
      bash -c "export DEBIAN_FRONTEND=noninteractive ; apt-get install -y krb5-user"
      bash -c "cat krb5.conf > /etc/krb5.conf"

    SCRIPT

    client.vm.provision "shell", inline: $script1

    #normal user script
    $script2 = <<-SCRIPT
      echo "usuario shell: `whoami`}"

      #setup para passwordless
      mkdir -p /home/vagrant/.ssh
      cp /vagrant/config-files/id_rsa /home/vagrant/.ssh/
      chmod 600 /home/vagrant/.ssh/id_rsa
      cp /vagrant/config-files/id_rsa.pub /home/vagrant/.ssh/
      cat /vagrant/config-files/id_rsa.pub >> /home/vagrant/.ssh/authorized_keys
      chown vagrant:vagrant /home/vagrant/.ssh/id_rsa
      chown vagrant:vagrant /home/vagrant/.ssh/id_rsa.pub

      mkdir -p /home/vagrant/ssl
      ### copia da chave publica da autoridade certificadora
      scp -o "StrictHostKeyChecking no" vagrant@ca:/home/vagrant/ssl/ca-cert /home/vagrant/ssl
      ### criacao da truststore importando o certificado
      export CLIPASS=senha-insegura ; keytool -keystore /home/vagrant/ssl/kafka.client.truststore.jks -alias CARoot -import -file /home/vagrant/ssl/ca-cert -storepass $CLIPASS -keypass $CLIPASS -noprompt

      mkdir -p /home/vagrant/keytabs
      scp -o "StrictHostKeyChecking no" vagrant@kerberos:/home/vagrant/keytabs/aplicacao1.user.keytab /home/vagrant/keytabs/
      scp -o "StrictHostKeyChecking no" vagrant@kerberos:/home/vagrant/keytabs/aplicacao2.user.keytab /home/vagrant/keytabs/
      scp -o "StrictHostKeyChecking no" vagrant@kerberos:/home/vagrant/keytabs/admin.user.keytab /home/vagrant/keytabs/

      ln -s /vagrant/aplicacao1/ aplicacao1
      ln -s /vagrant/aplicacao2/ aplicacao2

      #build do projeto producer
      cd ~/aplicacao1
      mvn clean package

      #build do projeto consumer
      cd ~/aplicacao2
      mvn clean package

    SCRIPT

    client.vm.provision "shell", inline: $script2, privileged: false

  end
end
