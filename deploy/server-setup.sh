#!/usr/bin/env bash
# =====================================================================
# 서버 초기 설정 (Ubuntu 22.04) - 한 번만 실행, 다시 실행해도 안전
#   - OpenJDK 21, Tomcat 10.1 (systemd: tomcat)
#   - Docker + Oracle XE 11g (컨테이너: wrt-oracle, 127.0.0.1:1521 에만 공개)
#   - 앱 DB 계정 wrt 생성, 접속 정보는 /etc/wrt/app.env (root 전용)
# 사용: sudo bash server-setup.sh
# =====================================================================
set -euo pipefail

TOMCAT_VERSION="10.1.52"
TOMCAT_DIR="/opt/tomcat"
ORACLE_IMAGE="gvenzl/oracle-xe:11-slim"
CONF_DIR="/etc/wrt"
UPLOAD_DIR="/var/lib/wrt/upload"

[ "$(id -u)" -eq 0 ] || { echo "sudo 로 실행하세요."; exit 1; }

echo "== 1. 패키지 설치 (Java 21, Docker) =="
export DEBIAN_FRONTEND=noninteractive
apt-get update -q
apt-get install -y -q openjdk-21-jre-headless docker.io curl ca-certificates
systemctl enable --now docker

echo "== 2. Tomcat ${TOMCAT_VERSION} =="
id tomcat >/dev/null 2>&1 || useradd --system --home-dir "$TOMCAT_DIR" --shell /usr/sbin/nologin tomcat
if [ ! -x "$TOMCAT_DIR/bin/catalina.sh" ]; then
  TGZ="apache-tomcat-${TOMCAT_VERSION}.tar.gz"
  URL="https://archive.apache.org/dist/tomcat/tomcat-10/v${TOMCAT_VERSION}/bin/${TGZ}"
  cd /tmp
  curl -fsSLO "$URL"
  curl -fsSLO "$URL.sha512"
  sha512sum -c "${TGZ}.sha512"
  mkdir -p "$TOMCAT_DIR"
  tar -xzf "$TGZ" -C "$TOMCAT_DIR" --strip-components=1
  rm -rf "$TOMCAT_DIR"/webapps/*          # 기본 예제·관리자 앱 제거
  rm -f "/tmp/$TGZ" "/tmp/$TGZ.sha512"
fi
chown -R tomcat:tomcat "$TOMCAT_DIR"

echo "== 3. 설정 파일 (/etc/wrt) =="
mkdir -p "$CONF_DIR" "$UPLOAD_DIR"
chown -R tomcat:tomcat /var/lib/wrt
chmod 755 "$CONF_DIR"

if [ ! -f "$CONF_DIR/oracle.env" ]; then
  ORACLE_PW="$(openssl rand -base64 18 | tr -dc 'A-Za-z0-9' | head -c 20)"
  APP_PW="$(openssl rand -base64 18 | tr -dc 'A-Za-z0-9' | head -c 20)"
  cat > "$CONF_DIR/oracle.env" <<EOF
ORACLE_PASSWORD=${ORACLE_PW}
APP_USER=wrt
APP_USER_PASSWORD=${APP_PW}
EOF
  cat > "$CONF_DIR/app.env" <<EOF
DB_URL=jdbc:oracle:thin:@127.0.0.1:1521:XE
DB_USER=wrt
DB_PASSWORD=${APP_PW}
EOF
fi
chmod 600 "$CONF_DIR/oracle.env" "$CONF_DIR/app.env"

# 앱 설정 (web.xml 값을 덮어씀) - GOOGLE_CLIENT_ID 는 여기서 채운다
mkdir -p "$TOMCAT_DIR/conf/Catalina/localhost"
if [ ! -f "$TOMCAT_DIR/conf/Catalina/localhost/ROOT.xml" ]; then
  cat > "$TOMCAT_DIR/conf/Catalina/localhost/ROOT.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<Context>
  <Parameter name="GOOGLE_CLIENT_ID" value="" override="false"/>
  <Parameter name="ALLOWED_DOMAIN" value="gsm.hs.kr" override="false"/>
  <Parameter name="AUTH_DEV_MODE" value="false" override="false"/>
  <Parameter name="UPLOAD_DIR" value="${UPLOAD_DIR}" override="false"/>
</Context>
EOF
fi
chown -R tomcat:tomcat "$TOMCAT_DIR/conf"

echo "== 4. Tomcat systemd 서비스 =="
cat > /etc/systemd/system/tomcat.service <<EOF
[Unit]
Description=Apache Tomcat (WRT)
After=network.target docker.service

[Service]
Type=forking
User=tomcat
Group=tomcat
EnvironmentFile=${CONF_DIR}/app.env
Environment=JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
Environment=CATALINA_HOME=${TOMCAT_DIR}
Environment=CATALINA_BASE=${TOMCAT_DIR}
Environment=CATALINA_PID=${TOMCAT_DIR}/temp/tomcat.pid
Environment="CATALINA_OPTS=-Xms256m -Xmx768m -Dfile.encoding=UTF-8"
ExecStart=${TOMCAT_DIR}/bin/startup.sh
ExecStop=${TOMCAT_DIR}/bin/shutdown.sh
PIDFile=${TOMCAT_DIR}/temp/tomcat.pid
Restart=on-failure

[Install]
WantedBy=multi-user.target
EOF
systemctl daemon-reload
systemctl enable tomcat

echo "== 5. Oracle XE 컨테이너 =="
if ! docker ps -a --format '{{.Names}}' | grep -qx wrt-oracle; then
  docker run -d --name wrt-oracle --restart unless-stopped \
    --env-file "$CONF_DIR/oracle.env" \
    -p 127.0.0.1:1521:1521 \
    --shm-size=1g \
    -v wrt-oracle-data:/u01/app/oracle/oradata \
    "$ORACLE_IMAGE"
fi
echo "DB 준비 대기..."
# grep -q 는 먼저 끝나 docker logs 가 SIGPIPE 로 실패하므로(pipefail) 로그를 변수로 받아 검사
oracle_ready() { local log; log="$(docker logs wrt-oracle 2>&1 || true)"; [[ "$log" == *"DATABASE IS READY TO USE"* ]]; }
for i in $(seq 1 90); do
  oracle_ready && break
  sleep 5
done
oracle_ready || { echo "Oracle 기동 실패"; docker logs --tail 50 wrt-oracle; exit 1; }

echo "== 6. 배포 스크립트 =="
install -m 755 "$(dirname "$0")/deploy.sh" /usr/local/bin/wrt-deploy
# 배포 계정(ubuntu)은 sudo 비밀번호 없이 wrt-deploy 만 실행 가능해도 되지만, 이 서버는 이미 NOPASSWD sudo 가 설정되어 있음

systemctl start tomcat
echo "== 완료 =="
