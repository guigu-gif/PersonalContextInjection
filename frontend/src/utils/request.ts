import axios from 'axios'

const request = axios.create({
  baseURL: 'http://localhost:8082',
  timeout: 120000
})

request.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) config.headers['authorization'] = token
  return config
})

request.interceptors.response.use(
  res => res.data,
  err => {
    if (err.response?.status === 401) {
      localStorage.removeItem('token')
      window.location.href = '/login'
    }
    return Promise.reject(err)
  }
)

export default request
