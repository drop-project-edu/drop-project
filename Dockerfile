FROM tomcat:9.0-jdk11

EXPOSE 8080

WORKDIR /usr/src/app

# install maven
RUN apt-get update && apt-get install -y maven

# setup environment variables
ENV DP_M2_HOME /usr/share/maven
ENV DP_MVN_REPO /usr/src/app/mvn_repository

COPY target/drop-project.war /usr/local/tomcat/webapps/ROOT.war

CMD ["catalina.sh", "run"]