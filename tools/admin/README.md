# Alta de la administradora del panel (RF-40)

Crea la cuenta de administradora y le asigna el custom claim `admin=true`. Es la
**única** forma de dar de alta una admin: el panel no tiene registro público ni
puede asignar roles.

## Llave de servicio — misma política que `tools/seed`

La llave de servicio da acceso administrativo total al proyecto Firebase. **Nunca**
dentro del repositorio. Reutiliza la misma llave que la siembra:

```bash
# Si aún no la tienes (Consola de Firebase → ⚙️ Configuración del proyecto →
# Cuentas de servicio → Generar nueva clave privada):
mkdir -p ~/.config/aurora-seed
mv ~/Downloads/aurora-31c37-firebase-adminsdk-*.json ~/.config/aurora-seed/service-account.json
chmod 600 ~/.config/aurora-seed/service-account.json
```

Barreras: ruta canónica fuera del repo, `.gitignore` veta `service-account*.json`
y `*-adminsdk-*.json`, y el script **aborta** si la ruta de la llave cae dentro
del repositorio (B-1).

## Crear la admin

```bash
cd tools/admin
npm ci   # o: npm install
export GOOGLE_APPLICATION_CREDENTIALS="$HOME/.config/aurora-seed/service-account.json"

# Cuenta nueva con contraseña temporal generada (se imprime una sola vez):
node create-admin.mjs --email admin@ejemplo.org

# O fijando la contraseña (mínimo 8 caracteres, RF-07):
node create-admin.mjs --email admin@ejemplo.org --password 'una-contraseña-fuerte'
```

- **Idempotente:** reejecutar sobre la misma cuenta solo reafirma el claim admin.
  Sobre una cuenta existente, sin `--password` no toca la contraseña; con
  `--password` la restablece.
- Tras asignar el claim, la cuenta debe **cerrar sesión y volver a entrar** (o
  refrescar el token) para que el panel lo reconozca. El panel ya fuerza el
  refresco del token al iniciar sesión, así que basta con volver a entrar.

## Requisito previo

Que **Email/Password** esté habilitado en Firebase Console → Authentication →
Sign-in method. (La app Android ya usa Auth, así que normalmente ya lo está.)

## Cómo verificar el claim

```bash
# Con la llave exportada, en un REPL de Node:
node -e "import('firebase-admin/app').then(async m=>{m.initializeApp({credential:m.applicationDefault()});const {getAuth}=await import('firebase-admin/auth');const u=await getAuth().getUserByEmail('admin@ejemplo.org');console.log(u.customClaims)})"
# Debe imprimir: { admin: true }
```
