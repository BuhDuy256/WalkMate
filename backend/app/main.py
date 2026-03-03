from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.api.routes import intent, match, session

app = FastAPI(title="WalkMate API", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(intent.router)
app.include_router(match.router)
app.include_router(session.router)


@app.get("/")
def read_root():
    return {"message": "WalkMate API V1", "status": "running"}


@app.get("/health")
def health_check():
    return {"status": "healthy"}
