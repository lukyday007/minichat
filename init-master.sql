-- repl_user라는 복제 전용 유저 생성 (비밀번호: repl_pass)
CREATE USER 'repl_user'@'%' IDENTIFIED WITH 'mysql_native_password' BY 'repl_pass';

-- 복제에 필요한 권한(REPLICATION SLAVE) 부여
GRANT REPLICATION SLAVE ON *.* TO 'repl_user'@'%';

-- 권한 적용
FLUSH PRIVILEGES;