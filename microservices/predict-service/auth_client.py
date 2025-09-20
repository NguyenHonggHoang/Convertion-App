# auth_client.py - Internal API token management for microservices
import os
import time
import logging
import requests
from datetime import datetime, timedelta
from threading import Lock
import jwt

logger = logging.getLogger(__name__)

class InternalApiTokenClient:
    """
    Client for managing internal API tokens in microservices.
    Handles token generation, caching, and automatic renewal.
    """
    
    def __init__(self, service_name, backend_url=None):
        self.service_name = service_name
        self.backend_url = backend_url or os.getenv("BACKEND_URL", "http://backend:8080")
        self.token_endpoint = f"{self.backend_url}/api/internal/token"
        self.validate_endpoint = f"{self.backend_url}/api/internal/validate"
        
        # Token cache
        self._token = None
        self._token_expires_at = None
        self._lock = Lock()
        
        # Config
        self.token_duration_minutes = int(os.getenv("TOKEN_DURATION_MINUTES", "60"))
        self.refresh_threshold_minutes = int(os.getenv("TOKEN_REFRESH_THRESHOLD_MINUTES", "10"))
        
    def get_token(self, force_refresh=False):
        """
        Get a valid internal API token, refreshing if necessary.
        """
        with self._lock:
            if not force_refresh and self._is_token_valid():
                return self._token
                
            return self._refresh_token()
    
    def _is_token_valid(self):
        """
        Check if current token is valid and not expiring soon.
        """
        if not self._token or not self._token_expires_at:
            return False
            
        # Check if token expires within refresh threshold
        refresh_time = datetime.now() + timedelta(minutes=self.refresh_threshold_minutes)
        return self._token_expires_at > refresh_time
    
    def _refresh_token(self):
        """
        Request a new token from the backend service.
        """
        try:
            data = {
                "serviceName": self.service_name,
                "durationMinutes": self.token_duration_minutes
            }
            
            response = requests.post(
                self.token_endpoint,
                params=data,
                timeout=10,
                headers={"Content-Type": "application/json"}
            )
            
            if response.status_code != 200:
                logger.error(f"Failed to get token: {response.status_code} - {response.text}")
                return None
                
            result = response.json()
            self._token = result["token"]
            
            # Parse expiration from JWT token
            try:
                # Decode without verification to get expiration
                decoded = jwt.decode(self._token, options={"verify_signature": False})
                exp_timestamp = decoded.get("exp")
                if exp_timestamp:
                    self._token_expires_at = datetime.fromtimestamp(exp_timestamp)
                else:
                    # Fallback: calculate based on duration
                    self._token_expires_at = datetime.now() + timedelta(minutes=self.token_duration_minutes)
            except:
                # Fallback expiration calculation
                self._token_expires_at = datetime.now() + timedelta(minutes=self.token_duration_minutes)
                
            logger.info(f"Retrieved new internal API token for {self.service_name}, expires at {self._token_expires_at}")
            return self._token
            
        except Exception as e:
            logger.error(f"Error refreshing token for {self.service_name}: {e}")
            return None
    
    def validate_token(self, token=None):
        """
        Validate a token with the backend service.
        """
        token_to_validate = token or self._token
        if not token_to_validate:
            return False
            
        try:
            response = requests.post(
                self.validate_endpoint,
                json={"token": token_to_validate},
                timeout=5
            )
            
            if response.status_code == 200:
                result = response.json()
                return result.get("valid", False)
            else:
                return False
                
        except Exception as e:
            logger.warning(f"Error validating token: {e}")
            return False
    
    def get_auth_headers(self):
        """
        Get headers for authenticated requests.
        """
        token = self.get_token()
        if token:
            return {"X-API-Key": token}
        else:
            logger.warning(f"No valid token available for {self.service_name}")
            return {}

# Global instance for predict-service
predict_service_auth = InternalApiTokenClient("predict-service")

def get_internal_api_headers():
    """
    Convenience function to get authentication headers for internal API calls.
    """
    return predict_service_auth.get_auth_headers()