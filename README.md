# storage

## Test containers environment

- install Docker Desktop
- in Docker Desktop
  - Settings -> General
    - turn on `Choose Virtual Machine Manager (VMM)` -> `WSL 2`
  - Settings -> Resources -> WSL Integration
    - allow installed WSL

## Deployment

Login - https://github.com/settings/tokens

```bash
docker login ghcr.io -u GITHUB_USERNAME
#  password -> PERSONAL_TOKEN
```

```bash
mvn clean package
docker build -t fabric-storage:local .

# list local images
docker image ls

# tag local
docker tag fabric-storage:local ghcr.io/kso-fabric/storage:0.1.0
docker tag fabric-storage:local ghcr.io/kso-fabric/storage:latest

# push
docker push ghcr.io/kso-fabric/storage:0.1.0
docker push ghcr.io/kso-fabric/storage:latest

# pull
docker pull ghcr.io/kso-fabric/storage:0.1.0

# validate architecture
docker image inspect fabric-storage:local --format '{{.Os}}/{{.Architecture}}'
```

Packges - https://github.com/orgs/kso-fabric/packages

## Install Java SDK on WSL 

```bash
# in WSL
sudo apt install unzip zip
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"

sdk list java # choose sdk
sdk install java 25.0.5.1+2-tem # identifier for example
```

If `java -version` does not work open `~/.bashrc` and add to end 
(`SDKMAN` part may be already in):

```bash
# Sdkman
export SDKMAN_DIR="$HOME/.sdkman"
[[ -s "$HOME/.sdkman/bin/sdkman-init.sh" ]] && source "$HOME/.sdkman/bin/sdkman-init.sh"

# Java
export JAVA_HOME="$HOME/.sdkman/candidates/java/current"
export PATH="$JAVA_HOME/bin:$PATH"
```



