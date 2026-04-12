#!/bin/bash
sed -i 's/\.defaultTools(actionTools, navigationTools)//' /home/foenix/workspace/spring-rag/src/main/java/com/example/korrag/service/RagService.java
sed -i 's/this.chatClient = chatClientBuilder/this.chatClient = chatClientBuilder.build();\n        this.actionTools = actionTools;\n        this.navigationTools = navigationTools;/' /home/foenix/workspace/spring-rag/src/main/java/com/example/korrag/service/RagService.java
sed -i 's/private final ToolEventPublisher toolEventPublisher;/private final ToolEventPublisher toolEventPublisher;\n    private final HrActionTools actionTools;\n    private final HrNavigationTools navigationTools;/' /home/foenix/workspace/spring-rag/src/main/java/com/example/korrag/service/RagService.java
sed -i 's/\.system(buildMainSystemPrompt())/\.tools(actionTools, navigationTools)\n                        \.system(buildMainSystemPrompt(userId))/' /home/foenix/workspace/spring-rag/src/main/java/com/example/korrag/service/RagService.java
sed -i 's/private String buildMainSystemPrompt()/private String buildMainSystemPrompt(String userId)/' /home/foenix/workspace/spring-rag/src/main/java/com/example/korrag/service/RagService.java
sed -i 's/당신은 대한민국 채용 HR 시스템의 유능한 AI 비서입니다./당신은 대한민국 채용 HR 시스템의 유능한 AI 비서입니다.\\n                [현재 세션 사용자 ID: %s]/' /home/foenix/workspace/spring-rag/src/main/java/com/example/korrag/service/RagService.java
sed -i 's/\"\"\";/\"\"\", userId);/' /home/foenix/workspace/spring-rag/src/main/java/com/example/korrag/service/RagService.java
