ARG JAVA_BASE_IMAGE=eclipse-temurin:25.0.4_7-jre
FROM ${JAVA_BASE_IMAGE}

RUN apt-get update && apt-get install -y default-mysql-client && apt-get clean

ENV RUN_USER=jdbbackup
ENV RUN_GROUP=jdbbackup
RUN groupadd -r ${RUN_GROUP} && useradd -g ${RUN_GROUP} -m -s /bin/bash ${RUN_USER}

WORKDIR /home/${RUN_USER}

ARG JAR_FILE
COPY ${JAR_FILE} jdbbackup.jar

RUN chown -R ${RUN_USER}:${RUN_USER} .
USER ${RUN_USER}

CMD ["sh","-c","exec java ${JAVA_OPTS} -jar jdbbackup.jar"]
