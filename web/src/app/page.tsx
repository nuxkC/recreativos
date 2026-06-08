import { redirect } from "next/navigation";

/**
 * El middleware ya redirige usuarios no autenticados a /login. Cuando esté
 * implementado el dashboard real (T-38) este placeholder se reemplazará.
 */
export default function HomePage() {
  redirect("/dashboard");
}
