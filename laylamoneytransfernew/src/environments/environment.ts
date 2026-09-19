export const environment = {
  production: false,
  apiUrl: 'http://localhost:8080/api',
  // TRU Connect JWT (st.js) secure card flow. OFF until backend credentials are configured and
  // sandbox-tested; when false the legacy redirect form is used (unchanged).
  trustJwtEnabled: false,
  // Trust st.js library URL (EU cluster). Only loaded when trustJwtEnabled is true.
  trustStJsUrl: 'https://cdn.eu.trustpayments.com/js/latest/st.js'
};
