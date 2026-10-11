import { useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import { useNavigate } from "react-router-dom";
import toast from "react-hot-toast";
import { AuthLayout } from "../components/layout/AuthLayout";
import { Card } from "../components/ui/Card";
import { Input } from "../components/ui/Input";
import { Button } from "../components/ui/Button";
import { useRegister } from "../features/auth/authQueries";
import { useAuthStore } from "../store/authStore";
import { PageTransition } from "../components/ui/PageTransition";

const schema = z
  .object({
    fullName: z
      .string()
      .trim()
      .min(2, "Full name must be at least 2 characters")
      .max(100, "Full name must be under 100 characters"),
    email: z
      .string()
      .trim()
      .email("Please enter a valid email address"),
    password: z
      .string()
      .min(6, "Password must be at least 6 characters")
      .max(72, "Password must be under 72 characters"),
    confirmPassword: z
      .string()
      .min(1, "Please confirm your password"),
  })
  .refine((data) => data.password === data.confirmPassword, {
    message: "Passwords do not match",
    path: ["confirmPassword"],
  });

type FormValues = z.infer<typeof schema>;

export const RegisterPage = () => {
  const navigate = useNavigate();
  const setAuth = useAuthStore((state) => state.setAuth);
  const registerMutation = useRegister();

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
  });

  const onSubmit = (values: FormValues) => {
    const timezone = Intl.DateTimeFormat().resolvedOptions().timeZone || "UTC";

    registerMutation.mutate(
      {
        fullName: values.fullName,
        email: values.email.toLowerCase(),
        password: values.password,
        timezone,
      },
      {
        onSuccess: (response) => {
          setAuth(response.token, response.refreshToken);
          toast.success("Welcome to TodoFlow! Your account has been created.");
          navigate("/");
        },
        onError: (error: any) => {
          const serverError =
            error.response?.data?.message ||
            (error.response?.data && typeof error.response.data === "object"
              ? Object.values(error.response.data)[0]
              : null) ||
            "Registration failed. Please check your details and try again.";
          toast.error(String(serverError));
        },
      }
    );
  };

  return (
    <AuthLayout title="Create account" subtitle="Start organizing your work">
      <PageTransition>
        <Card className="p-8">
          <form onSubmit={handleSubmit(onSubmit)}>
            <h1 className="text-2xl font-bold mb-6">Register</h1>

            <div className="mb-4">
              <Input
                placeholder="Full Name"
                autoComplete="name"
                {...register("fullName")}
              />
              {errors.fullName && (
                <p className="text-red-500 text-sm mt-1">
                  {errors.fullName.message}
                </p>
              )}
            </div>

            <div className="mb-4">
              <Input
                type="email"
                placeholder="Email"
                autoComplete="email"
                {...register("email")}
              />
              {errors.email && (
                <p className="text-red-500 text-sm mt-1">{errors.email.message}</p>
              )}
            </div>

            <div className="mb-4">
              <Input
                type="password"
                placeholder="Password (min 6 characters)"
                autoComplete="new-password"
                {...register("password")}
              />
              {errors.password && (
                <p className="text-red-500 text-sm mt-1">
                  {errors.password.message}
                </p>
              )}
            </div>

            <div className="mb-5">
              <Input
                type="password"
                placeholder="Confirm Password"
                autoComplete="new-password"
                {...register("confirmPassword")}
              />
              {errors.confirmPassword && (
                <p className="text-red-500 text-sm mt-1">
                  {errors.confirmPassword.message}
                </p>
              )}
            </div>

            <Button
              type="submit"
              disabled={registerMutation.isPending}
              className="w-full"
            >
              {registerMutation.isPending ? "Creating Account..." : "Create Account"}
            </Button>

            <Button
              type="button"
              variant="secondary"
              className="w-full mt-3"
              onClick={() => navigate("/login")}
            >
              Already have an account? Sign In
            </Button>
          </form>
        </Card>
      </PageTransition>
    </AuthLayout>
  );
};
