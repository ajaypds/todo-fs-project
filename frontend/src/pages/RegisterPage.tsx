import { useForm } from "react-hook-form";
import { z } from "zod";
import { zodResolver } from "@hookform/resolvers/zod";
import { register as registerUser } from "../features/auth/authApi";
import { useNavigate } from "react-router-dom";

const schema = z.object({
  fullName: z.string().min(2),
  email: z.email(),
  password: z.string().min(6),
});

type FormValues = z.infer<typeof schema>;

export const RegisterPage = () => {
  const navigate = useNavigate();

  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<FormValues>({
    resolver: zodResolver(schema),
  });

  const onSubmit = async (values: FormValues) => {
    try {
      await registerUser(values);

      navigate("/login");
    } catch (error) {
      console.error(error);

      alert("Registration failed");
    }
  };

  return (
    <div className="h-screen flex items-center justify-center">
      <form
        onSubmit={handleSubmit(onSubmit)}
        className="bg-white p-8 rounded-lg shadow-md w-96"
      >
        <h1 className="text-2xl font-bold mb-6">Register</h1>

        <div className="mb-4">
          <input
            placeholder="Full Name"
            className="w-full border p-3 rounded"
            {...register("fullName")}
          />

          {errors.fullName && (
            <p className="text-red-500 text-sm">{errors.fullName.message}</p>
          )}
        </div>

        <div className="mb-4">
          <input
            placeholder="Email"
            className="w-full border p-3 rounded"
            {...register("email")}
          />

          {errors.email && (
            <p className="text-red-500 text-sm">{errors.email.message}</p>
          )}
        </div>

        <div className="mb-4">
          <input
            type="password"
            placeholder="Password"
            className="w-full border p-3 rounded"
            {...register("password")}
          />

          {errors.password && (
            <p className="text-red-500 text-sm">{errors.password.message}</p>
          )}
        </div>

        <button
          type="submit"
          className="w-full bg-black text-white p-3 rounded"
        >
          Register
        </button>
      </form>
    </div>
  );
};
