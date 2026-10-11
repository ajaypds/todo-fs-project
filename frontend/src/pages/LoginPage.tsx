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

const schema = z.object({
  email: z.string().trim().email("Please enter a valid email address"),
  password: z.string().min(1, "Password is required"),
});

type FormValues = z.infer<typeof schema>;

export const LoginPage = () => {
  const navigate = useNavigate();

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
    loginMutation.mutate(
      {
        email: values.email.toLowerCase(),
        password: values.password,
      },
      {
        onSuccess: (response) => {
          setAuth(response.token, response.refreshToken);
          toast.success("Welcome back!");
          navigate("/");
        },
        onError: (error: any) => {
          const message =
            error.response?.data?.message ||
            "Invalid email or password. Please try again.";
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
