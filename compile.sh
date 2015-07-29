#!/bin/sh -e
#

printUsage() {
   echo "Usage : "
   echo "./compile.sh [-s | --skipTests] [-u URL | --url URL] BRANCH"
   echo "    -s : Skip test"
   echo "    -u URL : clone the given URL for retrieving sources"
   echo "    BRANCH : Branch to build"
   exit 1
}

MODE=local
ORIGIN=/origin
DESTINATION=/destination

for arg in "$@"
do
   case $arg in
      -s|--skipTests)
         SKIPTESTS="skipTests"
         ;;
      -u|--url)
         MODE="distant"
         REPO_URL=$2
         shift
         ;;
      -*)
         echo "Invalid option: -$OPTARG"
         printUsage
         ;;
      *)
         if ! [ -z "$1" ]; then
            BRANCH=$1
         fi
         ;;
   esac
   if [ "0" -lt "$#" ]; then
      shift
   fi
done

if [ -z "$BRANCH" ]; then
   echo "You must provide a BRANCH name"
   printUsage
fi

# Sources retrieval

if [ $MODE = "local" ]; then
   git clone $ORIGIN/. -b $BRANCH
   for i in `git submodule | cut -d' ' -f2`; do
      INITIALIZED=`cd $ORIGIN; git submodule status $i | cut -c1`
      if [ "$INITIALIZED" != "-" ]; then
         git config submodule.$i.url $ORIGIN/$i
      fi
   done
else
   git clone $REPO_URL . -b $BRANCH
fi
git submodule init
git submodule update

# Compilation

PROFILS_LIST=-Pcassandra,exclude-lucene,with-assembly,with-jetm 

PROJECTS_LIST=org.apache.james:apache-james-mpt-imapmailbox-cassandra,\
org.apache.james:apache-james-mpt-imapmailbox-cyrus,\
org.apache.james:apache-james-mpt-external-james,\
org.apache.james:apache-mime4j,\
org.apache.james:apache-jsieve-all,\
org.apache.james.jdkim:apache-jdkim,\
org.apache.james.jspf:apache-jspf,\
org.apache.james.jspf:apache-jspf-resolver,\
org.apache.james.jspf:apache-jspf-tester,\
org.apache.james.protocols:protocols-api,\
org.apache.james.protocols:protocols-smtp,\
org.apache.james.protocols:protocols-lmtp,\
org.apache.james.protocols:protocols-netty,\
org.apache.james.protocols:protocols-pop3,\
org.apache.james.protocols:protocols-imap,\
org.apache.james:apache-mailet-api,\
org.apache.james:apache-mailet-base,\
org.apache.james:mailetdocs-maven-plugin,\
org.apache.james:apache-mailet-crypto,\
org.apache.james:apache-mailet-standard,\
org.apache.james:apache-mailet-ai,\
org.apache.james:apache-james-mailbox-tika,\
org.apache.james:apache-james-mailbox-elasticsearch,\
org.apache.james:apache-james-mailbox-cassandra,\
org.apache.james:james-server-util,\
org.apache.james:james-server-cli,\
org.apache.james:james-server-spring,\
org.apache.james:james-server-core,\
org.apache.james:james-server-lifecycle-api,\
org.apache.james:james-server-mailbox-adapter,\
org.apache.james:james-server-filesystem-api,\
org.apache.james:james-server-mailetcontainer-api,\
org.apache.james:james-server-mailetcontainer-camel,\
org.apache.james:james-server-mailets,\
org.apache.james:james-server-dnsservice-api,\
org.apache.james:james-server-dnsservice-dnsjava,\
org.apache.james:james-server-dnsservice-library,\
org.apache.james:james-server-data-api,\
org.apache.james:james-server-data-library,\
org.apache.james:james-server-fetchmail,\
org.apache.james:james-server-protocols-imap4,\
org.apache.james:james-server-protocols-library,\
org.apache.james:james-server-protocols-lmtp,\
org.apache.james:james-server-protocols-pop3,\
org.apache.james:james-server-protocols-smtp,\
org.apache.james:james-server-queue-api,\
org.apache.james:james-server-queue-file,\
org.apache.james:james-server-queue-jms,\
org.apache.james:james-server-queue-activemq


if [ "$SKIPTESTS" = "skipTests" ]; then
   mvn clean install -T1C -DskipTests $PROFILS_LIST -am -pl $PROJECTS_LIST
else
    mvn clean install $PROFILS_LIST -am -pl $PROJECTS_LIST
fi

# Retrieve result

if [ $? -eq 0 ]; then
   cp modules/james/app/target/james-server-app-*-app.zip $DESTINATION
fi
