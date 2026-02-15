# AWS 배포 준비 및 기본 세팅 가이드

본 문서는 **EC2 + Docker Compose** 기준으로 AWS 배포 환경을 준비하는 절차를 정리한다.
(추후 ECS/Fargate로 확장 가능)

---

## 1. 목표 아키텍처
- EC2 1대 (Ubuntu 22.04 LTS 권장)
- 보안그룹으로 SSH 및 서비스 포트 제한
- Docker + Docker Compose 설치
- SSH 접속 후 배포 스크립트로 서비스 운영

---

## 2. AWS 기본 준비

### 2.1 AWS 계정/권한
- AWS 콘솔 접근 권한 확보
- IAM 사용자 생성(관리자 권한 또는 EC2/VPC 관련 권한)

### 2.2 VPC / 서브넷
- 기본 VPC 사용 가능
- Public Subnet에 EC2 생성

---

## 3. EC2 생성 및 보안 설정

### 3.1 EC2 인스턴스 생성
- AMI: Ubuntu Server 22.04 LTS
- 인스턴스 타입: t3.small 이상 권장
- 스토리지: 20~40GB (로그/이미지 저장 고려)

### 3.2 키 페어(.pem) 생성
- EC2 생성 과정에서 Key Pair 생성
- 형식: `RSA` + `.pem`
- 로컬에 안전하게 저장

### 3.3 보안 그룹(Security Group)
필수 인바운드 규칙:
- SSH (22): 본인 IP만 허용
- 서비스 포트: 8081(또는 실제 서비스 포트)
- HTTP/HTTPS 필요 시: 80/443

예시:
- TCP 22 → `YOUR_IP/32`
- TCP 8081 → `0.0.0.0/0` (테스트용)
- TCP 80,443 → `0.0.0.0/0` (프로덕션용)

---

## 4. SSH 접속

### 4.1 로컬에서 키 권한 설정
```bash
chmod 400 ./your-key.pem
```

### 4.2 접속
```bash
ssh -i ./your-key.pem ubuntu@<EC2_PUBLIC_IP>
```

---

## 5. EC2 초기 세팅

### 5.1 패키지 업데이트
```bash
sudo apt update && sudo apt upgrade -y
```

### 5.2 Docker 설치
```bash
sudo apt install -y ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(. /etc/os-release && echo $VERSION_CODENAME) stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

### 5.3 Docker 권한
```bash
sudo usermod -aG docker ubuntu
newgrp docker
```

### 5.4 스크립트 기반 일관 세팅 (권장)
매번 동일한 방식으로 세팅하기 위해 레포에 준비된 스크립트를 사용한다.

로컬에서 원격 서버에 직접 실행:
```bash
./scripts/aws/run_bootstrap_remote.sh miyou-dev
```

원격 서버에서 수동 실행:
```bash
scp scripts/aws/bootstrap_base.sh miyou-dev:/tmp/bootstrap_base.sh
ssh miyou-dev "sudo bash /tmp/bootstrap_base.sh"
```

주요 환경 변수(선택):
- `APP_DIR` (기본: `/opt/app`)
- `APP_USER` (기본: `ubuntu`)
- `INSTALL_SSM_AGENT` (기본: `true`)
- `RUN_UPGRADE` (기본: `false`)

---

## 6. 배포 구성 방식 (권장)

### 6.1 서버 디렉토리 구조
```
/opt/app
  ├─ docker-compose.yml
  ├─ .env
  └─ deploy.sh
```

### 6.2 환경 변수(.env)
- .env는 **서버에만 존재**
- Git에는 커밋하지 않음

---

## 7. SSH 포트 관리(보안 강화)

### 7.1 기본 SSH 포트 변경 (선택)
- `/etc/ssh/sshd_config` 수정 후 포트 변경
- 보안 그룹에서도 새 포트 허용

### 7.2 Fail2ban 적용 (선택)
```bash
sudo apt install -y fail2ban
```

---

## 8. 배포 스크립트 예시

`/opt/app/deploy.sh`
```bash
#!/usr/bin/env bash
set -euo pipefail

docker compose pull

docker compose up -d

docker image prune -f
```

권한 부여:
```bash
chmod +x /opt/app/deploy.sh
```

---

## 9. 헬스체크

### 9.1 기본 확인
```bash
curl http://localhost:8081/actuator/health
```

### 9.2 시스템 리소스 확인
```bash
docker ps
free -h
```

---

## 10. 운영 고려사항
- 로그 수집(CloudWatch Agent or Docker 로그 정책)
- 스냅샷/백업 전략
- 보안 패치 주기
- 도메인/SSL 적용(ACM or Certbot)

---

## 11. 다음 단계
1) EC2 생성 + SSH 접속 완료
2) Docker/Compose 설치 완료
3) 레지스트리(GHCR) 인증 방식 확정
4) CI/CD 파이프라인 작성

필요 시: 본 프로젝트용 `docker-compose.yml` 및 CI 파이프라인을 바로 작성해줄 수 있다.
