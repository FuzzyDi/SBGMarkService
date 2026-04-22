"""Local dev entrypoint: python run.py"""
from app import create_app
from app.config import Settings

if __name__ == "__main__":
    settings = Settings()
    app = create_app()
    app.run(host="0.0.0.0", port=settings.port, debug=True)
