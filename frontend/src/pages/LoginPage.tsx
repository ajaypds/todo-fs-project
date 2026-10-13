import { useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import { useAuthStore } from "../store/authStore";
import { useNavigate } from "react-router-dom";
import { AuthLayout } from "../components/layout/AuthLayout";
import { Card } from "../components/ui/Card";
import { Input } from "../components/ui/Input";
import { Button } from "../components/ui/Button";
import { useLogin } from "../features/auth/authQueries";
import { PageTransition } from "../components/ui/PageTransition";
import toast from "react-hot-toast";

import { useState } from "react";

const schema = z.object({
  email: z.string().trim().email("Please enter a valid email address"),
  password: z.string().min(1, "Password is required"),
});

type FormValues = z.infer<typeof schema>;

export const LoginPage = () => {
  const navigate = useNavigate();
  const [unverifiedEmail, setUnverifiedEmail] = useState<string | null>(null);

  const setAuth = useAuthStore((state) => state.setAuth);

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
  });

  const loginMutation = useLogin();

  const onSubmit = (values: FormValues) => {
    setUnverifiedEmail(null);
    loginMutation.mutate(
      {
        email: values.email.toLowerCase(),
        password: values.password,
      },
      {
        onSuccess: (response) => {
          if (response.token && response.refreshToken) {
            setAuth(response.token, response.refreshToken);
            toast.success("Welcome back!");
            navigate("/");
          }
        },
        onError: (error: any) => {
          const message =
            error.response?.data?.message ||
            "Invalid email or password. Please try again.";
          if (typeof message === "string" && message.toLowerCase().includes("not verified")) {
            setUnverifiedEmail(values.email.toLowerCase());
          }
          toast.error(message);
        },
      }
    );
  };

  return (
    <AuthLayout title="Welcome back" subtitle="Sign in to continue">
      <PageTransition>
        <Card className="p-8">
          {/* <div className="h-screen flex items-center justify-center"> */}
          <form
            onSubmit={handleSubmit(onSubmit)}
            // className="bg-white p-8 rounded-lg shadow-md w-96"
          >
            <h1 className="text-2xl font-bold mb-6">Login</h1>

            {unverifiedEmail && (
              <div className="mb-4 p-3.5 rounded-lg border border-amber-500/30 bg-amber-500/10 text-xs text-amber-700 dark:text-amber-300 flex flex-col gap-2">
                <span className="font-medium">
                  Your email ({unverifiedEmail}) has not been verified yet.
                </span>
                <Button
                  type="button"
                  variant="secondary"
                  className="w-full text-xs py-1.5 h-auto font-medium"
                  onClick={() =>
                    navigate(
                      `/verify-email?email=${encodeURIComponent(unverifiedEmail)}`
                    )
                  }
                >
                  Verify Email with Code
                </Button>
              </div>
            )}

            <div className="mb-4">
              <Input
                placeholder="Email"
                // className="w-full border p-3 rounded"
                {...register("email")}
              />

              {errors.email && (
                <p className="text-red-500 text-sm">{errors.email.message}</p>
              )}
            </div>

            <div className="mb-4">
              <Input
                type="password"
                placeholder="Password"
                // className="w-full border p-3 rounded"
                {...register("password")}
              />

              {errors.password && (
                <p className="text-red-500 text-sm">
                  {errors.password.message}
                </p>
              )}
            </div>

            <Button
              type="submit"
              disabled={loginMutation.isPending}
              className="w-full"
            >
              {loginMutation.isPending ? "Signing in..." : "Sign In"}
            </Button>
            <Button
              type="button"
              variant="secondary"
              className="w-full mt-3"
              onClick={() => navigate("/register")}
            >
              Register
            </Button>
          </form>
          {/* </div> */}
        </Card>
      </PageTransition>
    </AuthLayout>
  );
};
