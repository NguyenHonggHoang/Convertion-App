import React, { forwardRef } from 'react';
import ReCAPTCHA from 'react-google-recaptcha';
import { Box, Typography } from '@mui/material';

// Plain JS version to avoid requiring TypeScript in builds.
const Captcha = forwardRef(({ onCaptchaChange, onExpired, onVerify }, ref) => {
  const RECAPTCHA_SITE_KEY =
    process.env.REACT_APP_RECAPTCHA_V2_SITE_KEY ||
    process.env.REACT_APP_CAPTCHA_SITEKEY ||
    '6LeIxAcTAAAAAJcZVRqyHh71UMIEGNQ_MXjiZKhI'; // Google's test site key

  const handleChange = (token) => {
    if (onCaptchaChange) onCaptchaChange(token);
    if (onVerify) onVerify(token || '');
  };

  return (
    <Box sx={{ mt: 2, mb: 2 }}>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
        Please complete the verification below:
      </Typography>
      <ReCAPTCHA
        ref={ref}
        sitekey={RECAPTCHA_SITE_KEY}
        onChange={handleChange}
        onExpired={onExpired}
        theme="light"
        size="normal"
      />
    </Box>
  );
});

Captcha.displayName = 'Captcha';

export default Captcha;
