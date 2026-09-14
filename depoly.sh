#!/bin/bash

# === 配置信息 ===
MAVEN_CMD="/Users/lzyy011/apache-maven-3.9.11/bin/mvn"
PROJECT_DIR="/Users/lzyy011/workspace/projects/firefly-store"
LOCAL_WAR_PATH="$PROJECT_DIR/target/firefly-store.war"

REMOTE_USER="lzyy"
REMOTE_HOST="192.168.10.241"
REMOTE_PORT=10115
REMOTE_KEY="$HOME/.ssh/115-3080"
REMOTE_WAR_DIR="/home/lzyy/work-space/DockerWebApps/channel/webapps"
REMOTE_WAR_PATH="$REMOTE_WAR_DIR/firefly-store.war"

# === 1. 本地项目打包 ===
cd "$PROJECT_DIR" || exit 1
"$MAVEN_CMD" clean package -DskipTests || { echo "Maven 打包失败"; exit 1; }

# === 2. 远程删除旧包 ===
ssh -i "$REMOTE_KEY" -p $REMOTE_PORT -o StrictHostKeyChecking=no $REMOTE_USER@$REMOTE_HOST "rm -f $REMOTE_WAR_PATH"

# === 3. 上传新包到服务器 ===
scp -i "$REMOTE_KEY" -P $REMOTE_PORT "$LOCAL_WAR_PATH" $REMOTE_USER@$REMOTE_HOST:"$REMOTE_WAR_DIR/"

# === 4. (可选) 检查上传结果 ===
ssh -i "$REMOTE_KEY" -p $REMOTE_PORT -o StrictHostKeyChecking=no $REMOTE_USER@$REMOTE_HOST "ls -lh $REMOTE_WAR_PATH"
