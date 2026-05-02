let amapPromise

const DEFAULT_PLUGINS = ['AMap.Scale', 'AMap.ToolBar', 'AMap.Geocoder', 'AMap.Walking', 'AMap.Riding']

export function loadAmap(plugins = DEFAULT_PLUGINS) {
  const key = import.meta.env.VITE_AMAP_KEY
  const securityCode = import.meta.env.VITE_AMAP_SECURITY_CODE

  if (!key) {
    return Promise.reject(new Error('missing_amap_key'))
  }

  if (securityCode) {
    window._AMapSecurityConfig = {
      securityJsCode: securityCode
    }
  }

  if (window.AMap) {
    return loadPlugins(plugins)
  }

  if (!amapPromise) {
    amapPromise = new Promise((resolve, reject) => {
      const script = document.createElement('script')
      const pluginQuery = plugins.length ? `&plugin=${plugins.join(',')}` : ''
      script.src = `https://webapi.amap.com/maps?v=2.0&key=${encodeURIComponent(key)}${pluginQuery}`
      script.async = true
      script.onload = () => {
        if (!window.AMap) {
          reject(new Error('amap_not_available'))
          return
        }
        resolve(window.AMap)
      }
      script.onerror = () => reject(new Error('amap_load_failed'))
      document.head.appendChild(script)
    })
  }

  return amapPromise.then(() => loadPlugins(plugins))
}

function loadPlugins(plugins) {
  if (!plugins.length || !window.AMap?.plugin) return Promise.resolve(window.AMap)
  return new Promise((resolve) => {
    window.AMap.plugin(plugins, () => resolve(window.AMap))
  })
}
