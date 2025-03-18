pipeline {
    agent any

    environment {
        DOCKER_IMAGE_NAME = 'jennahan/spring-mvc-app' // Docker Hub에 저장할 이미지 이름
        DOCKER_CREDENTIALS_ID = 'funddockercicd' // Docker Hub 자격 증명 ID
        EC2_IP = '43.200.51.20' // EC2 인스턴스 IP 주소
        EC2_USER = 'ubuntu' // EC2 인스턴스 사용자
        CONTAINER_NAME = 'stockSpringContainer' // 사용할 컨테이너 이름
        JASYPT_ENCRYPTOR_PASSWORD = credentials('jasypt_password') // Jenkins에 저장된 Jasypt 암호화 비밀번호
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'main', credentialsId: 'fundcicd', url: 'https://github.com/FunD-StockProject/StockProject-Backend.git'
            }
        }

        stage('Prepare Keystore') {
            steps {
                script {
                    sh """
                    cp /home/ubuntu/keystore.p12 ${WORKSPACE}/keystore.p12
                    """
                }
            }
        }

        stage('Fetch score.py, wc.py, update.py, stockindex.py, requirements.txt, fear-and-greed-0.4.tar.gz') {
            steps {
                sshagent (credentials: ['EC2_API_SSH']) { // EC2_API_SSH는 Jenkins에 등록된 SSH 키
                    sh """
                    # EC2에서 score.py와 requirements.txt 가져오기
                    scp -o StrictHostKeyChecking=no ${EC2_USER}@${EC2_IP}:/home/ubuntu/score.py ${WORKSPACE}/score.py
                    scp -o StrictHostKeyChecking=no ${EC2_USER}@${EC2_IP}:/home/ubuntu/wc.py ${WORKSPACE}/wc.py
                    scp -o StrictHostKeyChecking=no ${EC2_USER}@${EC2_IP}:/home/ubuntu/update.py ${WORKSPACE}/update.py
                    scp -o StrictHostKeyChecking=no ${EC2_USER}@${EC2_IP}:/home/ubuntu/stockindex.py ${WORKSPACE}/stockindex.py
                    scp -o StrictHostKeyChecking=no ${EC2_USER}@${EC2_IP}:/home/ubuntu/hotsearch.py ${WORKSPACE}/hotsearch.py
                    scp -o StrictHostKeyChecking=no ${EC2_USER}@${EC2_IP}:/home/ubuntu/summary.py ${WORKSPACE}/summary.py
                    scp -o StrictHostKeyChecking=no ${EC2_USER}@${EC2_IP}:/home/ubuntu/requirements.txt ${WORKSPACE}/requirements.txt
                    scp -o StrictHostKeyChecking=no ${EC2_USER}@${EC2_IP}:/home/ubuntu/fear-and-greed-0.4.tar.gz ${WORKSPACE}/fear-and-greed-0.4.tar.gz
                    """
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                script {
                    dockerImage = docker.build("${DOCKER_IMAGE_NAME}", "--build-arg JASYPT_ENCRYPTOR_PASSWORD=${JASYPT_ENCRYPTOR_PASSWORD} .")
                }
            }
        }

        stage('Push to Docker Hub') {
            steps {
                script {
                    docker.withRegistry('https://index.docker.io/v1/', "${DOCKER_CREDENTIALS_ID}") {
                        dockerImage.push("${env.BUILD_NUMBER}")
                        dockerImage.push('latest')
                    }
                }
            }
        }

        stage('Deploy to EC2') {
            steps {
                sshagent (credentials: ['EC2_API_SSH']) {
                    sh """
                    ssh -o StrictHostKeyChecking=no ${EC2_USER}@${EC2_IP} << 'EOF'
                        # Docker 최신 이미지 가져오기
                        sudo docker pull ${DOCKER_IMAGE_NAME}:latest

                        # 기존 컨테이너 중지 및 삭제
                        sudo docker stop ${CONTAINER_NAME} || true
                        sudo docker rm ${CONTAINER_NAME} || true

                        # 컨테이너 실행
                        sudo docker run -d --name ${CONTAINER_NAME} -p 443:443 \
                            -e JASYPT_ENCRYPTOR_PASSWORD=${JASYPT_ENCRYPTOR_PASSWORD} \
                            -e TZ=Asia/Seoul \
                            ${DOCKER_IMAGE_NAME}:latest

                        # 불필요한 이미지 제거
                        IMAGES=\$(sudo docker images -f 'dangling=true' -q)
                        if [ -n "\$IMAGES" ]; then
                            sudo docker rmi -f \$IMAGES
                        else
                            echo "No dangling images to remove."
                        fi

                        # 이전 버전의 이미지 삭제
                        sudo docker images | grep ${DOCKER_IMAGE_NAME} | awk '{print \$3}' | tail -n +3 | xargs --no-run-if-empty sudo docker rmi -f
EOF
                    """
                }
            }
        }
    }

    post {
        always {
            cleanWs()
        }
    }
}