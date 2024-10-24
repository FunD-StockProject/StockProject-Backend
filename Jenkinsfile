pipeline {
    agent any

    environment {
        DOCKER_IMAGE_NAME = 'jennahan/spring-mvc-app' // Docker Hub에 저장할 이미지 이름
        DOCKER_CREDENTIALS_ID = 'funddockercicd' // Docker Hub 자격 증명 ID
        EC2_IP = '15.165.237.52' // EC2 인스턴스 IP 주소
        EC2_USER = 'ubuntu' // EC2 인스턴스 사용자
        CONTAINER_NAME = 'stockSpringContainer' // 사용할 컨테이너 이름
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


        stage('Push Docker Image to Docker Hub') {
            steps {
                script {
                    withCredentials([usernamePassword(credentialsId: "${DOCKER_CREDENTIALS_ID}", passwordVariable: 'DOCKER_PASSWORD', usernameVariable: 'DOCKER_USERNAME')]) {
                        sh 'echo $DOCKER_PASSWORD | docker login -u $DOCKER_USERNAME --password-stdin'
                        sh 'docker push ${DOCKER_IMAGE_NAME}:${BUILD_NUMBER}'
                    }
                }
            }
        }

        stage('Deploy to EC2') {
            steps {
                script {
                    // SSH로 EC2에 접속하여 Docker 컨테이너를 업데이트
                    sh """
                    ssh -o StrictHostKeyChecking=no ${EC2_USER}@${EC2_IP} << EOF
                    # 기존 컨테이너가 있으면 중지하고 삭제
                    docker stop ${CONTAINER_NAME} || true
                    docker rm ${CONTAINER_NAME} || true

                    # 새로운 컨테이너 실행
                    docker pull ${DOCKER_IMAGE_NAME}:${BUILD_NUMBER}
                    docker run -d --name ${CONTAINER_NAME} -p ${PORT}:${PORT} ${DOCKER_IMAGE_NAME}:${BUILD_NUMBER}
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