import type { ReactNode } from "react";

import { motion } from "framer-motion";

type Props = {
  title: string;

  subtitle: string;

  children: ReactNode;
};

export const AuthLayout = ({ title, subtitle, children }: Props) => {
  return (
    <div
      className="
        min-h-screen
        grid lg:grid-cols-2
        bg-background
      "
    >
      <div
        className="
          hidden xl:flex
          flex-col justify-between
          bg-primary
          text-white
          p-12
        "
      >
        <div>
          <h1 className="text-3xl font-bold tracking-tight">TodoFlow</h1>
        </div>

        <div>
          <h2
            className="
              text-5xl font-bold
              leading-tight
              max-w-lg
            "
          >
            Organize your work and life.
          </h2>

          <p
            className="
              mt-6 text-lg
              text-white/70
              max-w-md
            "
          >
            A modern productivity workspace inspired by the best SaaS
            experiences.
          </p>
        </div>

        <div className="text-sm text-white/50">
          Built with Spring Boot + React
        </div>
      </div>

      <div
        className="
          flex items-center
          justify-center
          p-6
        "
      >
        <motion.div
          initial={{
            opacity: 0,
            y: 12,
          }}
          animate={{
            opacity: 1,
            y: 0,
          }}
          transition={{
            duration: 0.3,
          }}
          className="
            w-full max-w-md
          "
        >
          <div className="mb-8">
            <h1 className="text-3xl font-bold tracking-tight">{title}</h1>

            <p className="text-muted mt-2">{subtitle}</p>
          </div>

          {children}
        </motion.div>
      </div>
    </div>
  );
};
