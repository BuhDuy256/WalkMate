# Backend Folder Structure

```text
com.walkmate (backend/src/main/java/com/walkmate)
|   Application.java
|   
+---application
|       .gitignore
|       
+---domain
|   +---intent
|   |       .gitignore
|   |       
|   +---session
|   |       .gitignore
|   |       
|   +---user
|   |       .gitignore
|   |       
|   \---valueobject
|           .gitignore
|           
+---infrastructure
|   +---config
|   |       .gitignore
|   |       
|   +---exception
|   |       .gitignore
|   |       
|   +---logging
|   |       LayerLoggingAspect.java
|   |       MdcFilter.java
|   |       
|   +---repository
|   |       .gitignore
|   |       
|   \---security
|           .gitignore
|           
\---presentation
    +---controller
    |       .gitignore
    |       
    +---dto
    |   +---request
    |   |       .gitignore
    |   |       
    |   \---response
    |           .gitignore
    |           
    \---mapper
            .gitignore
```
