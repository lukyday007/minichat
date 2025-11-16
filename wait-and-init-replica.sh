#!/bin/bash

# 마스터 DB의 'repl_user'가 준비될 때까지 2초마다 재시도하며 대기
until mysql -h mysql-master -u repl_user -prepl_pass -e "SELECT 1"; do
  echo "Waiting for master mysql (repl_user) to be ready..."
  sleep 2
done

echo "Master is ready. Configuring replication."

# 마스터에 연결하여 복제 설정 시작
mysql -u root -ppassword -e "
  CHANGE MASTER TO
      MASTER_HOST='mysql-master',
      MASTER_USER='repl_user',
      MASTER_PASSWORD='repl_pass',
      MASTER_AUTO_POSITION=1;
  START SLAVE;
"