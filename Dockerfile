FROM openjdk:11-jre

RUN apt-get update && apt-get install -y default-mysql-client
RUN apt-get clean

ENV RUN_USER jdbbackup
ENV RUN_GROUP jdbbackup
RUN groupadd -r ${RUN_GROUP} && useradd -g ${RUN_GROUP} -m -s /bin/bash ${RUN_USER}

WORKDIR /home/${RUN_USER}

COPY target/jdbbackup.jar .

RUN chown -R ${RUN_USER}:${RUN_USER} .
USER ${RUN_USER}

CMD ["sh","-c","exec java ${JAVA_OPTS} -jar jdbbackup.jar"]
