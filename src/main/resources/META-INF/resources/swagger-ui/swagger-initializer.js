/* Force Swagger UI to use ?configUrl=... (or ?url=...) if provided */
window.onload = function() {
  function getParam(name) {
    return new URL(window.location.href).searchParams.get(name);
  }

  const configUrl = getParam("configUrl");
  const singleUrl = getParam("url"); // fallback

  const uiOpts = {
    dom_id: '#swagger-ui',
    deepLinking: true,
    presets: [SwaggerUIBundle.presets.apis, SwaggerUIStandalonePreset],
    layout: "StandaloneLayout"
  };

  if (configUrl) {
    uiOpts.configUrl = configUrl;           // ← 우리 서버의 /swagger-config/... 를 그대로 사용
  } else if (singleUrl) {
    uiOpts.url = singleUrl;                 // ← /external-specs/{name}를 직접 지정한 경우
  }

  window.ui = SwaggerUIBundle(uiOpts);
};
