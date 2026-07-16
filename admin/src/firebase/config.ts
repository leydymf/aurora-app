// Inicialización del SDK web de Firebase para el panel (proyecto aurora-31c37).
//
// Solo se usan Auth (RF-40) y Firestore. Sin Analytics ni ningún servicio que
// registre telemetría: el panel es interno de la operación y no perfila a nadie.
import { initializeApp } from 'firebase/app'
import { getAuth } from 'firebase/auth'
import { getFirestore } from 'firebase/firestore'

// La config web NO es secreta (viaja en cualquier bundle de cliente); la
// seguridad la dan firestore.rules + el custom claim `admin`. Ver .env.example.
const firebaseConfig = {
    apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
    authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
    projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
    storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
    messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
    appId: import.meta.env.VITE_FIREBASE_APP_ID,
}

const app = initializeApp(firebaseConfig)

export const auth = getAuth(app)
export const db = getFirestore(app)
