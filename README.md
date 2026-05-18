<h1 align="center"> 🛡️ API Rate Limiter (Spring AOP) - PoC </h1>

<p align="center">
  <img src="https://img.shields.io/badge/Java_21-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21">
  <img src="https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=spring&logoColor=white" alt="Spring Boot">
  <img src="https://img.shields.io/badge/React-20232a.svg?style=for-the-badge&logo=react&logoColor=%2361DAFB" alt="React">
  <img src="https://img.shields.io/badge/Testes-JUnit_5%20%7C%20Mockito-success?style=for-the-badge" alt="Testes">
</p>

## 🎯 Sobre o Projeto (System Design)

Em arquiteturas de microserviços e APIs públicas, o **Rate Limiting** (Limitação de Taxa) é crucial para prevenir abusos, ataques DDoS e garantir a disponibilidade do serviço. 

Este projeto é uma **Prova de Conceito (PoC)** de um sistema de Rate Limiting customizado. Em vez de depender de soluções externas (como API Gateways prontos), o mecanismo de controle de tráfego foi construído do zero no nível da aplicação, provando conceitos avançados de engenharia de software no ecossistema Spring.

---

## 🏛️ Decisões Arquiteturais e Padrões Aplicados

O maior diferencial técnico deste projeto reside na forma como a interceptação de requisições foi implementada:

### 1. Programação Orientada a Aspectos (Spring AOP)
O bloqueio de requisições **não suja o código de negócio**. Foi criada uma anotação customizada `@RateLimited(cost = X)` acoplada a um `RateLimiterAspect`. 
* **O ganho:** O Aspecto intercepta a requisição usando `@Around` antes que ela alcance o Controller. Se a chave de API (`X-API-KEY`) não tiver saldo suficiente, a execução é abortada imediatamente.

### 2. Controle de Custo de Endpoints e Planos
Diferentes rotas possuem "pesos" diferentes. Uma requisição a `/api/v1/heavy-data` consome mais limite da cota do usuário do que um simples `/api/v1/data`. Isso é gerido através de um modelo relacional sólido no banco de dados.

### 3. Tratamento Centralizado de Erros
Implementação de um `@ControllerAdvice` (`GlobalExceptionHandler`) para capturar exceções como `RateLimitException` na camada de serviço e traduzi-las automaticamente para a resposta HTTP padrão da indústria: **429 Too Many Requests**.

---

## 🛡️ Qualidade de Software e Cobertura

O núcleo do limitador de taxa é rigorosamente coberto por testes automatizados, aplicando as melhores práticas de Engenharia de Software:
* **Mocks e Stubs:** Uso extensivo do Mockito para isolar a camada de serviço.
* **Testes de Interceptação:** Validação do comportamento HTTP (`MockMvc`) para garantir que o AOP retorne `429 Too Many Requests` corretamente sob carga excessiva.
* **Cobertura (JaCoCo):** Validação de caminhos felizes e falhas na validação da API Key.

---

## ⚙️ Como Executar Localmente

### Backend (Spring Boot)
```bash
cd backend
./mvnw clean test  # Executa a suíte de testes de limite de taxa
./mvnw spring-boot:run
```


### Frontend (Dashboard em React/Vite)
Um painel simples para consumo de chaves e visualização de bloqueios em tempo real.

```bash
cd frontend
npm install
npm run dev
```

## 👨‍💻 Autor

**Augusto Ortigoso Barbosa**

  * **GitHub:** [github.com/supp3rguto](https://github.com/supp3rguto)
  * **LinkedIn:** [linkedin.com/in/augusto-barbosa-769602194](https://www.linkedin.com/in/augusto-barbosa-769602194)
