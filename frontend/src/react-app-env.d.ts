declare const process: {
  env: {
    NODE_ENV: 'development' | 'production' | 'test';
    PUBLIC_URL: string;
    REACT_APP_CAPTCHA_PROVIDER?: string;
    REACT_APP_CAPTCHA_SITEKEY?: string;
    [key: string]: string | undefined;
  };
};