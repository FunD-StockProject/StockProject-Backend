pipeline {
    agent any

    environment {
        DOCKER_IMAGE_NAME = 'jennahan/spring-mvc-app' // Docker Hub에 저장할 이미지 이름
        DOCKER_CREDENTIALS_ID = 'funddockercicd' // Docker Hub 자격 증명 ID
        EC2_IP = '15.165.237.52' // EC2 인스턴스 IP 주소
        EC2_USER = 'ubuntu' // EC2 인스턴스 사용자 ex ubuntu
        CONTAINER_NAME = 'stockSpringContainer' // 컨테이너 이름
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'main', credentialsId: 'fundcicd', url: 'https://github.com/FunD-StockProject/StockProject-Backend.git'
            }
        }

        stage('Build Docker Image') {
            steps {
                script {
                    dockerImage = docker.build("${DOCKER_IMAGE_NAME}")
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
                sshagent (credentials: ['EC2_API_SSH']) { // 'aws_key'는 Jenkins에 저장된 SSH 자격 증명 ID
                    sh """
                    ssh -o StrictHostKeyChecking=no ${EC2_USER}@${EC2_IP} << 'EOF'
                        sudo docker pull ${DOCKER_IMAGE_NAME}:latest
                        sudo docker stop ${CONTAINER_NAME} || true
                        sudo docker rm ${CONTAINER_NAME} || true
                        sudo docker run -d --name ${CONTAINER_NAME} -p 8080:8080 ${DOCKER_IMAGE_NAME}:latest

                        IMAGES=\$(sudo docker images -f 'dangling=true' -q)
                        if [ -n "\$IMAGES" ]; then
                            sudo docker rmi -f \$IMAGES
                        else
                            echo "No dangling images to remove."
                        fi
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