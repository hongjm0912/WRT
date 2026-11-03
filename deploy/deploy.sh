#!/usr/bin/env bash
# =====================================================================
# WAR 배포 - GitHub Actions 가 호출 (서버에는 /usr/local/bin/wrt-deploy 로 설치됨)
# 사용: sudo wrt-deploy /tmp/ROOT.war
#   1) 현재 ROOT.war 백업 → 2) 교체 후 재시작 → 3) /api/v1/me 가 401 을 주는지 확인
#   확인 실패 시 이전 버전으로 되돌림
# =====================================================================
set -euo pipefail

NEW_WAR="${1:?WAR 경로를 넘겨 주세요}"
TOMCAT_DIR="/opt/tomcat"
WEBAPPS="$TOMCAT_DIR/webapps"
BACKUP="/var/lib/wrt/ROOT.war.prev"
HEALTH_URL="http://127.0.0.1:8080/api/v1/me"

[ -s "$NEW_WAR" ] || { echo "WAR 파일이 없습니다: $NEW_WAR"; exit 1; }

health() {
  for i in $(seq 1 30); do
    code=$(curl -s -o /dev/null -w '%{http_code}' "$HEALTH_URL" || true)
    [ "$code" = "401" ] && return 0     # 로그인 안 한 상태의 정상 응답
    sleep 2
  done
  return 1
}

install_war() {
  systemctl stop tomcat
  rm -rf "$WEBAPPS/ROOT" "$WEBAPPS/ROOT.war"
  install -o tomcat -g tomcat -m 644 "$1" "$WEBAPPS/ROOT.war"
  systemctl start tomcat
}

[ -f "$WEBAPPS/ROOT.war" ] && cp "$WEBAPPS/ROOT.war" "$BACKUP"

install_war "$NEW_WAR"
if health; then
  echo "배포 성공"
  rm -f "$NEW_WAR"
  exit 0
fi

echo "헬스체크 실패 - 최근 로그:"
tail -n 40 "$TOMCAT_DIR/logs/catalina.out" || true
if [ -f "$BACKUP" ]; then
  echo "이전 버전으로 되돌립니다."
  install_war "$BACKUP"
  health && echo "롤백 완료" || echo "롤백 후에도 응답 없음"
fi
exit 1
