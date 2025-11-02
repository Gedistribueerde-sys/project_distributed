#!/bin/bash
set -e

# Build server en client
echo "Building server..."
cd server
mvn clean install
cd ..

echo "Building client..."
cd client
mvn clean install
cd ..
clear
# terminal 1
echo "===== STARTING SERVER ====="
java -cp server/target/classes org.example.Main

# terminal 2
echo "===== STARTING CLIENT ====="
java -cp client/target/classes org.example.Main