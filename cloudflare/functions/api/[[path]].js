import { handleApi } from '../../src/api.js'

export const onRequest = ({ request, env }) => handleApi(request, env)
